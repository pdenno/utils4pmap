(ns pdenno.utils4pmap
  "Implement a pmap-like function that accepts a timeout. Read caveat!"
  (:require [clojure.core.async :as async :refer [chan alts!! go timeout >! thread]]))

;;; Caveat: I think I've learned that what I'm trying to do can't be done.
;;;         Java doesn't have a reliable way to stop a process. I've tried
;;;         .stop. Its use is much discouraged and .stop-ing does cause weird
;;;         behavior. Running the busy-fn1 (see below) will invoke the code
;;;         for future-cancel, but the thread will ignore the interrupt. You can see this
;;;         because diag-interrupted won't be modified.  There is some discussion
;;;         here https://stackoverflow.com/questions/671049/how-do-you-kill-a-thread-in-java
;;;         about a volatile boolean field that the thread might listen to, but I haven't
;;;         conceived of a way to wrap code to make use of it. 
;;;
;;;         sleepy-fn1, unlike busy-fn1, does handle the interrupt.
;;;
;;;         Regarding the three attempts: probably all of them would work if any one of them worked.
;;;         The interrupt problem has to be solved. 

(def diag-interrupted (atom []))

;;; Attempt 1
(declare pmap-update-fn)
(defn pmap-timeout1
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started.
   Runs as many futures in parallel as possible for the hardware. Returns a vector of results."
  ([func members timeout]
   (pmap-timeout1 func members timeout (+ 2 (.. Runtime getRuntime availableProcessors))))
  ([func members timeout nprocessors]
   (reset! diag-interrupted [])
   (let [to-run      (atom (vec members))
         results     (atom [])
         running-cnt (atom 0)]
     (while (not-empty @to-run) ; busy loop mapping over results; not so good. 
       (when (< @running-cnt nprocessors)
         (let [mem (first @to-run)
               p   (promise)]
           (println "starting " p)
           (swap! running-cnt inc)
           (swap! to-run #(vec (rest %)))
           (swap! results conj {::fut (future
                                        (try (do (deliver p (System/currentTimeMillis))
                                                 (func mem))
                                             (catch InterruptedException e
                                               (swap! diag-interrupted #(conj % {:me mem}))
                                               {:timeout2 mem}))) ; POD value is ignored.
                                :prom p
                                :mem mem})))
       (swap! results (fn [r] (vec (doall (map #(pmap-update-fn % running-cnt timeout) r))))))
     ;; Wait for everyone to finish/timeout. 
     (while (some #(::fut %) @results)
       (swap! results (fn [r] (vec (doall (map #(pmap-update-fn % running-cnt timeout) r))))))
     @results)))

(defn ^:private pmap-update-fn
  "Return a (possibly new) value for the results vector member."
  [m running-cnt timeout]
  (cond (not (::fut m)) 
        m,
        (future-done? (::fut m))
        (do (swap! running-cnt dec)
            (println "**finishing " (:prom m))
            (deref (::fut m))),
        (> (System/currentTimeMillis)
           (+ @(:prom m) timeout))
        (do (swap! running-cnt dec)
            (println "**CANCELING " (:prom m))
            (future-cancel (::fut m))  
            {:timeout (:mem m)})   
        :else m))

;;; Try these
#_(pmap-timeout1 sleepy-fn1
               (repeatedly 8 #(+ 10 (* 1000 (rand-int 6))))
               5000)

;;; https://stackoverflow.com/questions/671049/how-do-you-kill-a-thread-in-java
;;; This one will will hang threads. The bug with this is that
;;; the busy ones won't ever interrupt.
;;; (You can check diag-interrupted to verify this.)
#_(pmap-timeout1 busy-fn1 (range 10) 5000)

;;;=================== Attempt 2 =============================
#_(defn pmap-timeout2
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started."
  ([func members maxtime] (pmap-timeout2 func members maxtime :timeout))
  ([func members maxtime timeout-key]
   (let [chan&prom (map #(let [c (chan)
                               p (promise)]
                           (go (deliver p (System/currentTimeMillis))
                               (>! c {::val (func %)}))              
                           [c p])
                        members)]
     ;; This was designed to gets around futures not .stop-ing. Still doesn't stop.
     (doall
      (map (fn [mem [c p]]
             (let [launched (deref p) ; blocks on unlaunched, not perfect.
                   remaining (max (- maxtime (- (System/currentTimeMillis) launched)) 1)
                   [v _] (alts!! [c (timeout remaining)])]
               (if (contains? v ::val)
                 (::val v)
                 {timeout-key mem})))
           members
           chan&prom)))))

;;;=================== Attempt 3 =============================
#_(defn pmap-timeout3
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started."
  ([func members maxtime] (pmap-timeout3 func members maxtime :timeout))
  ([func members maxtime timeout-key]
   (let [chan&prom (map #(let [p (promise)
                               zippy (println "promised " p) ; <=============== If sleepy-fn seems to do one at a time. 
                               c (thread (deliver p (System/currentTimeMillis))  ; POD not sure how these are scheduled...
                                         {::val (func %)})]               ; ...will they all start at once?
                           [c p])
                        members)]
     ;; This was designed to gets around futures not .stop-ing. Still doesn't stop.
     (doall
      (map (fn [mem [c p]]
             (let [launched (deref p) ; blocks on unlaunched, not perfect.
                   zippy (println "launched " p) 
                   remaining (max (- maxtime (- (System/currentTimeMillis) launched)) 1)
                   [v _] (alts!! [c (timeout remaining)])]
               (if (contains? v ::val)
                 (::val v)
                 {timeout-key mem})))
           members
           chan&prom)))))

(defn sleepy-fn1
  "Sleeps, returns argument."
  [n]
  (Thread/sleep n)
  n)

(defn busy-fn1
  "Stays busy, never finishes."
  [_]
  (while (< (rand-int 10) 100)
    (+ 1 (rand-int 5))))
