(ns lein-modules.inheritance
  (:use [lein-modules.common :only (config parent)])
  (:require [leiningen.core.project :as prj]))

(defn compositor
  "Returns a reducing function that turns a non-composite profile into
   a composite, e.g. {:test {:a 1}} becomes {:test
   [:test-foo] :test-foo {:a 1}} for a project named 'foo'. Composite
   profiles are simply concatenated"
  [project]
  (fn [m [k v]]
    (if (prj/composite-profile? v)
      (update-in m [k] (comp vec distinct concat) v)
      (let [n (keyword (format "%s%s-%s" (or (namespace k) "") (name k) (:name project)))]
        (assoc (update-in m [k] #(vec (cons n %))) n v)))))

(defn compositize-profiles
  "Return a profile map containing all the profiles found in the
  project and its ancestors, resulting in standard profiles,
  e.g. :test and :dev, becoming composite"
  [project]
  (loop [p project, result nil]
    (if (nil? p)
      result
      (recur (parent p)
        (reduce (compositor p) result
          (conj (select-keys (:modules p) [:inherited])
            (dissoc (:profiles (meta p)) :user :leiningen/test)))))))

(defn inherit
  "Add profiles from parents, setting any :inherited ones if found,
  where a parent profile overrides a grandparent, guarding recursive
  middleware calls with a metadata flag.
  See https://github.com/technomancy/leiningen/issues/1151"
  [project]
  (if (-> project meta ::modules-inherited)
    project
    (let [compost (compositize-profiles project)]
      (-> (prj/add-profiles project compost)
        (vary-meta assoc ::modules-inherited true)
        (vary-meta update-in [:profiles] merge compost)
        (prj/set-profiles (if (:inherited compost)
                            [:inherited :default]
                            [:default]))))))
