(ns lt.objs.sidebar
  (:require [lt.object :as object]
            [lt.objs.tabs :as tabs]
            [lt.objs.command :as cmd]
            [lt.objs.animations :as anim]
            [lt.objs.canvas :as canvas]
            [lt.util.dom :as dom]
            [lt.util.cljs :refer [->dottedkw]]
            [crate.binding :refer [map-bound bound subatom]])
  (:require-macros [lt.macros :refer [defui]]))

(def default-width 200)

(defui sidebar-item [this item]
  (let [{:keys [label]} @item]
    [:li {:class (bound this #(when (= item (:active %))
                                "current"))}
                        label])
  :click (fn [e]
           (object/raise this :toggle item)
           (object/raise item :toggle e)))

(defui sidebar-tabs [this tabs]
  [:ul#sidebar
   (for [[_ t] tabs]
     (sidebar-item this t))])

(defui vertical-grip [this]
  [:div.vertical-grip {:draggable "true"}]
  :dragstart (fn [e]
               (object/raise this :start-drag))
  :dragend (fn [e]
               (object/raise this :end-drag))
  :drag (fn [e]
          (object/raise this :width! e)
             ))

(object/behavior* ::no-anim-on-drag
                  :triggers #{:start-drag}
                  :reaction (fn [this]
                              (anim/off)))

(object/behavior* ::reanim-on-drop
                  :triggers #{:end-drag}
                  :reaction (fn [this]
                              (anim/on)))

(object/behavior* ::width!
                  :triggers #{:width!}
                  :throttle 5
                  :reaction (fn [this e]
                              (when-not (= 0 (.-clientX e))
                                (let [width (if (= (:side @this) :left)
                                              (.-clientX e)
                                              (- (dom/width js/document.body) (.-clientX e)))]
                                  (object/merge! tabs/multi {(:side @this) width})
                                  (object/merge! this {:width width
                                                       :max-width width})
                                  ))))

(object/behavior* ::pop-transient
                  :triggers #{:pop!}
                  :reaction (fn [this]
                              (object/raise this :close!)))

(object/behavior* ::open!
                  :triggers #{:open!}
                  :reaction (fn [this]
                              (object/merge! this {:width (:max-width @this)})
                              (object/merge! tabs/multi {(:side @this) (+ (:max-width @this) )})))

(object/behavior* ::close!
                  :triggers #{:close!}
                  :reaction (fn [this]
                              (object/merge! tabs/multi {(:side @this) 0})
                              (object/merge! this {:active nil
                                                   :transients (list)
                                                   :width 0})))

(object/behavior* ::item-toggled
                  :triggers #{:toggle}
                  :reaction (fn [this item {:keys [force? transient? soft?]}]
                              (if (or (not= item (:active @this))
                                      force?)
                                (do
                                  (object/merge! this {:active item
                                                       :prev (when transient?
                                                               (or (:prev @this ) (:active @this) :none))})
                                  (object/raise this :open!)
                                  (when-not soft?
                                    (object/raise item :focus!)))
                                (object/raise this :close!))
                              ))

(defn active-content [active]
  (when active
    (object/->content active)))

(defn ->width [width]
  (str (or width 0) "px"))

(object/object* ::sidebar
                :tags #{:sidebar}
                :items {}
                :width 0
                :side :left
                :transients '()
                :max-width default-width
                :init (fn [this]
                        [:div#side
                         [:div.content-wrapper {:style {:width (bound (subatom this :width) ->width)}}
                          [:div.content
                           (bound (subatom this :active) active-content)]
                          (vertical-grip this)]
                          ]))

(object/object* ::right-bar
                :items {}
                :tags #{:sidebar}
                :width 0
                :side :right
                :max-width 300
                :init (fn [this]
                         [:div#right-bar {:style {:width (bound (subatom this :width) ->width)}}
                          (vertical-grip this)
                          [:div.content
                           (bound (subatom this :active) active-content)]]))

(object/tag-behaviors :sidebar [::item-toggled ::width! ::open! ::close! ::no-anim-on-drag ::reanim-on-drop ::pop-transient])

(def sidebar (object/create ::sidebar))
(def rightbar (object/create ::right-bar))

(canvas/add! sidebar)
(canvas/add! rightbar)

(defn add-item [bar item]
  (object/update! bar [:items] assoc (:order @item) item))

(cmd/command {:command :close-sidebar
              :desc "Sidebar: close"
              :hidden true
              :exec (fn []
                      (object/raise rightbar :close!))})