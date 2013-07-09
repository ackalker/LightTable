(ns lt.objs.animations
  (:require [lt.object :as object]
            [lt.util.dom :as dom]))

(def $body (dom/$ :body))
(def force-off false)

(defn on []
  (when-not force-off
    (dom/add-class $body :animated)))

(defn off []
  (when-not force-off
    (dom/remove-class $body :animated)))

(defn on? []
  (when-not force-off
    (dom/has-class? $body :animated)))

(object/behavior* ::animate-on-init
                  :triggers #{:init}
                  :reaction (fn [app]
                              (on)))

(object/behavior* ::toggle-animations
                  :desc "Enable or disable UI animations"
                  :triggers #{:object.instant}
                  :type :user
                  :reaction (fn [this active?]
                              (set! force-off (not active?))
                              (if active?
                                (on)
                                (off))))

(object/tag-behaviors :app [::animate-on-init])