(ns pdenno.utils4pmap)

;;; Attempt 1
(declare pmap-utils-fn)
(defn pmap-timeout1
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started.
   Runs as many futures in parallel as possible for the hardware. Returns a vector of results."
  ([func members timeout]
   (pmap-timeout func members timeout (+ 2 (.. Runtime getRuntime availableProcessors))))
  ([func members timeout nprocessors]
   (let [to-run      (atom (vec members))
         results     (atom [])
         running-cnt (atom 0)]
     (while (not-empty @to-run)
       (when (< @running-cnt nprocessors)
         (let [mem (first @to-run)
               p   (promise)]
           (swap! running-cnt inc)
           (swap! to-run #(vec (rest %)))
           (swap! results conj {::fut (future
                                        (try (let [t (Thread/currentThread)]
                                               (deliver p {:thread t ; POD thread no longer used.
                                                           :started (System/currentTimeMillis)})
                                               (func mem))
                                             (catch InterruptedException e
                                               {:timeout mem}))) ; POD redundant, maybe ignored.
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
           (+ (:started @(:prom m)) timeout))
        (do (swap! running-cnt dec)
            (.interrupt (:thread @(:prom m))) ; try...
            (.stop (:thread @(:prom m)))      ; every-...
            (future-cancel (::fut m))          ; -thing. 
            {:timeout (:mem m)})       ; POD redundant
        :else m))

;;;=================== Attempt 2 =============================
(defn pmap-timeout2
  "Like (pmap func coll) except that it returns {:timeout <member>} for those members of coll
   for which func does not complete in timeout milliseconds after that member is started."
  ([func members maxtime] (pmap-timeout func members maxtime :timeout))
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
             (let [launched (deref p)
                   remaining (max (- maxtime (- (System/currentTimeMillis) launched)) 1)
                   [v _] (alts!! [c (timeout remaining)])]
               (if (contains? v ::val)
                 (::val v)
                 {timeout-key mem})))
           members
           chan&prom)))))
