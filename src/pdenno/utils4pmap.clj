(ns pdenno.utils4pmap
  "Implement a pmap-like function that accepts a timeout. Read caveat in README.md!")

;;;         This code is still under development. If you have thoughts on how it might be improved,
;;;         let me know through github issues. 

;;;         There is some discussion here
;;;         https://stackoverflow.com/questions/671049/how-do-you-kill-a-thread-in-java
;;;         about a volatile boolean field that the thread might listen to, but I haven't
;;;         conceived of a way to wrap code to make use of it. 

(declare pmap-update-fn)
(defn pmap-timeout
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started.
   Runs as many futures in parallel as possible for the hardware. Returns a vector of results."
  ([func members timeout]
   (pmap-timeout func members timeout (+ 2 (.. Runtime getRuntime availableProcessors))))
  ([func members timeout nprocessors]
   (let [to-run      (atom (vec members))
         results     (atom [])
         running-cnt (atom 0)]
     (while (not-empty @to-run) ; busy loop mapping over results; not so good. 
       (when (< @running-cnt nprocessors)
         (let [mem (first @to-run)
               p   (promise)]
           (swap! running-cnt inc)
           (swap! to-run #(vec (rest %)))
           (swap! results conj {::fut (future
                                        (try (do (deliver p (System/currentTimeMillis))
                                                 (func mem))
                                             (catch InterruptedException e
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
            (deref (::fut m))),
        (> (System/currentTimeMillis)
           (+ @(:prom m) timeout))
        (do (swap! running-cnt dec)
            (future-cancel (::fut m))  
            {:timeout (:mem m)})   
        :else m))

;;;=================== Attempt 2 =============================
;;;  (:require [clojure.core.async :as async :refer [chan alts!! go timeout >! thread]]))
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

