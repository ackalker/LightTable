(ns lt.objs.sidebar.workspace
  (:require [lt.object :as object]
            [lt.objs.command :as cmd]
            [lt.objs.settings :as settings]
            [lt.objs.context :as ctx]
            [lt.objs.files :as files]
            [lt.objs.workspace :as workspace]
            [lt.objs.opener :as opener]
            [lt.objs.popup :as popup]
            [lt.objs.sidebar :as sidebar]
            [lt.util.dom :as dom]
            [lt.util.cljs :refer [->dottedkw]]
            [crate.binding :refer [bound subatom]]
            [clojure.string :as string])
  (:require-macros [lt.macros :refer [defui]]))

(def active-dialog nil)
(def gui (js/require "nw.gui"))

(defn menu-item [opts]
  (let [mi (.-MenuItem gui)]
    (mi. (clj->js opts))))

(defn menu [items]
  (let [m (.-Menu gui)
        menu (m.)]
    (doseq [i items]
      (.append menu (menu-item i)))
    menu))

(defn show-menu [m x y]
  (.popup m x y))

(defn files-and-folders [path]
  (let [fs (workspace/files-and-folders path)]
    {:files (mapv #(object/create ::workspace.file %) (:files fs))
     :folders (mapv #(object/create ::workspace.folder %) (:folders fs))}))

(defn root-folder [path]
  (-> (object/create ::workspace.folder path)
      (object/add-tags [:workspace.folder.root])))

(defn root-file [path]
  (-> (object/create ::workspace.file path)
      (object/add-tags [:workspace.file.root])))

(defn remove-child [p child]
  (if (object/has-tag? child :workspace.file)
    (object/update! p [:files] (fn [cur] (vec (remove #{child} cur))))
    (object/update! p [:folders] (fn [cur] (vec (remove #{child} cur))))))

(object/behavior* ::add-ws-folder
                  :triggers #{:workspace.add.folder!}
                  :reaction (fn [this path]
                              (object/raise workspace/current-ws :add.folder! path)
                              ))

(object/behavior* ::add-ws-file
                  :triggers #{:workspace.add.file!}
                  :reaction (fn [this path]
                              (object/raise workspace/current-ws :add.file! path)
                              (object/raise (first (object/by-tag :opener)) :open! path)
                              ))

(object/behavior* ::on-open-ls
                  :triggers #{:open!}
                  :reaction (fn [this]
                              (object/merge! this {:open? true})
                              (when-not (:realized? @this)
                                (object/merge! this {:realized? true})
                                (object/merge! this (files-and-folders (:path @this))))))

(object/behavior* ::refresh
                  :triggers #{:refresh!}
                  :reaction (fn [this]
                              (doseq [f (concat (:files @this) (:folders @this))]
                                (object/destroy! f))
                              (object/raise workspace/current-ws :refresh (:path @this))
                              (object/merge! this (files-and-folders (:path @this)))
                              ))

(object/behavior* ::on-close
                  :triggers #{:close!}
                  :reaction (fn [this]
                              (object/merge! this {:open? false})))

(object/behavior* ::on-open-file
                  :triggers #{:open!}
                  :reaction (fn [this]
                              (object/raise opener/opener :open! (:path @this))))

(object/behavior* ::on-remove
                  :triggers #{:remove!}
                  :reaction (fn [this item]
                              (if (object/has-tag? item :workspace.folder)
                                (object/raise workspace/current-ws :remove.folder! (:path @item))
                                (object/raise workspace/current-ws :remove.file! (:path @item)))))

(object/behavior* ::on-clear
                  :triggers #{:clear!}
                  :reaction (fn [this]
                              (object/raise workspace/current-ws :clear!)))

(object/behavior* ::on-ws-add
                  :triggers #{:add}
                  :reaction (fn [ws f]
                              (if (files/file? f)
                                (object/update! tree [:files] conj (root-file f))
                                (object/update! tree [:folders] conj (root-folder f)))))

(object/behavior* ::on-ws-remove
                  :triggers #{:remove}
                  :reaction (fn [ws f]
                              (let [item (find-by-path f)]
                                (if (files/file? f)
                                  (object/update! tree [:files] (fn [cur] (vec (remove #{item} cur))))
                                  (object/update! tree [:folders] (fn [cur] (vec (remove #{item} cur)))))
                                (object/destroy! item))))

(object/behavior* ::on-ws-set
                  :triggers #{:set}
                  :reaction (fn [ws]
                              (let [{:keys [folders files]} @ws]
                                (object/merge! tree {:files (mapv root-file files)
                                                     :folders (mapv root-folder folders)}))))

(defn find-by-path [path]
  (first (filter #(= (:path @%) path) (object/by-tag :tree-item))))

(object/behavior* ::watched.delete
                  :triggers #{:watched.delete}
                  :reaction (fn [ws path]
                              (when-let [child (find-by-path path)]
                                (when-let [p (find-by-path (files/parent path))]
                                  (remove-child p child))
                                (object/destroy! child))))

(object/behavior* ::watched.create
                  :triggers #{:watched.create}
                  :reaction (fn [ws path]
                              (when-not (and (find-by-path path)
                                             (not (re-seq files/ignore-pattern (files/basename path))))
                                (when-let [parent (find-by-path (files/parent path))]
                                  (when (:realized? @parent)
                                    (if (files/dir? path)
                                      (object/update! parent [:folders] conj (object/create ::workspace.folder path))
                                      (object/update! parent [:files] conj (object/create ::workspace.file path)))
                                    )))))

(object/behavior* ::on-menu
                  :triggers #{:menu!}
                  :reaction (fn [this e]
                              (let [items (sort-by :order (object/raise-reduce this :menu-items []))]
                                (-> (menu items)
                                    (show-menu (.-clientX e) (.-clientY e))))))

(object/behavior* ::on-root-menu
                  :triggers #{:menu-items}
                  :reaction (fn [this items]
                              (conj items
                                    {:type "separator"
                                     :order 9}
                                    {:label "Remove from workspace"
                                            :order 10
                                            :click (fn [] (object/raise tree :remove! this))})
                              ))

(object/behavior* ::subfile-menu
                  :triggers #{:menu-items}
                  :reaction (fn [this items]
                              (conj items {:label "Rename"
                                          :order 1
                                          :click (fn [] (object/raise this :rename!))}
                                         {:label "Delete"
                                          :order 2
                                          :click (fn [] (object/raise this :delete!))})))

(object/behavior* ::subfolder-menu
                  :triggers #{:menu-items}
                  :reaction (fn [this items]
                              (conj items
                                    {:label "New file"
                                     :order 0
                                     :click (fn [] (object/raise this :new-file!))}
                                    {:label "Rename"
                                     :order 2
                                     :click (fn [] (object/raise this :rename!))}
                                    {:type "separator"
                                     :order 3}
                                    {:label "New folder"
                                     :order 4
                                     :click (fn [] (object/raise this :new-folder!))}
                                    {:label "Delete folder"
                                     :order 5
                                     :click (fn [] (object/raise this :delete!))}
                                    {:label "Refresh folder"
                                     :order 6
                                     :click (fn [] (object/raise this :refresh!))}
                                    )))

(object/behavior* ::delete-file
                  :triggers #{:delete!}
                  :reaction (fn [this]
                              (files/delete! (:path @this))
                              (dom/remove (object/->content this))
                              (object/destroy! this)))

(object/behavior* ::force-delete-folder
                  :triggers #{:force-delete!}
                  :reaction (fn [this]
                              (files/delete! (:path @this))
                              (dom/remove (object/->content this))
                              (object/destroy! this)))

(object/behavior* ::delete-folder
                  :triggers #{:delete!}
                  :reaction (fn [this]
                              (popup/popup! {:header "Delete this folder?"
                                            :body (str "This will delete " (:path @this) ", which cannot be undone.")
                                            :buttons [{:label "Delete folder"
                                                       :action (fn [] (object/raise this :force-delete!))}
                                                      popup/cancel-button]})))

(object/behavior* ::new-file!
                  :triggers #{:new-file!}
                  :reaction (fn [this]
                              (let [ext (if-let [ffile (-> @this :files first)]
                                          (when-let [path (-> ffile deref :path)] (files/ext path))
                                          "txt")
                                    path (files/join (:path @this) (str "untitled." ext))
                                    final-path (files/next-available-name path)
                                    folder (object/create ::workspace.file final-path)]
                                (object/update! this [:files] conj folder)
                                (object/merge! this {:open? true})
                                (files/save final-path "")
                                (object/raise opener/opener :open! final-path)
                                (object/raise folder :rename!))))

(object/behavior* ::new-folder!
                  :triggers #{:new-folder!}
                  :reaction (fn [this]
                              (let [path (files/join (:path @this) "NewFolder")
                                    final-path (files/next-available-name path)
                                    folder (object/create ::workspace.folder final-path)]
                                (object/update! this [:folders] conj folder)
                                (object/merge! this {:open? true})
                                (files/mkdir final-path)
                                (object/raise folder :rename!))))

(object/behavior* ::rename-folder
                  :triggers #{:rename}
                  :reaction (fn [this n]
                              (let [path (:path @this)
                                    neue (files/join (files/parent path) n)]
                                (when-not (= path neue)
                                  (if (files/exists? neue)
                                    (popup/popup! {:header "Folder already exists."
                                                   :body (str "The folder " neue " already exists, you'll have to pick a different name.")
                                                   :buttons [{:label "ok"
                                                              :action (fn []
                                                                        (object/raise this :rename.cancel)
                                                                        (object/raise this :rename!))}]})
                                    (let [root? (object/has-tag? this :workspace.folder.root)]
                                      (object/merge! this {:path neue :realized? false})
                                      (files/move! path neue)
                                      (object/raise this :refresh!)
                                      (if root?
                                        (object/raise workspace/current-ws :rename! path neue)
                                        (object/raise workspace/current-ws :watched.rename path neue))
                                      ))))))

(object/behavior* ::rename-file
                  :triggers #{:rename}
                  :reaction (fn [this n]
                              (let [path (:path @this)
                                    neue (files/join (files/parent path) n)]
                                (when-not (= path neue)
                                  (if (files/exists? neue)
                                    (popup/popup! {:header "File already exists."
                                                   :body (str "The file" neue " already exists, you'll have to pick a different name.")
                                                   :buttons [{:label "ok"
                                                              :action (fn []
                                                                        (object/raise this :rename.cancel)
                                                                        (object/raise this :rename!))}]})
                                    (do
                                      (if (or (object/has-tag? this :workspace.folder.root)
                                              (object/has-tag? this :workspace.file.root))
                                        (object/raise workspace/current-ws :rename! path neue)
                                        (object/raise workspace/current-ws :watched.rename path neue))
                                      (files/move! path neue)
                                      (object/merge! this {:path neue})))))))

(object/behavior* ::start-rename
                  :triggers #{:rename!}
                  :reaction (fn [this]
                              (object/merge! this {:renaming? true})
                              (let [input (dom/$ :input (object/->content this))
                                    len (count (files/without-ext (files/basename (:path @this))))]
                                (dom/focus input)
                                (dom/selection input 0 len "forward"))))

(object/behavior* ::rename-focus
                  :triggers #{:rename.focus}
                  :reaction (fn [this]
                              (ctx/in! :tree.rename this)))

(object/behavior* ::rename-submit
                  :triggers #{:rename.submit!}
                  :reaction (fn [this]
                              (let [val (-> (dom/$ :input (object/->content this))
                                            (dom/val))]
                                (object/merge! this {:renaming? false})
                                (object/raise this :rename val))))

(object/behavior* ::rename-blur
                  :triggers #{:rename.blur}
                  :reaction (fn [this]
                              (ctx/out! :tree.rename)
                              (when (:renaming? @this)
                                (object/raise this :rename.submit!))))

(object/behavior* ::rename-cancel
                  :triggers #{:rename.cancel!}
                  :reaction (fn [this]
                              (object/merge! this {:renaming? false})
                              ))

(object/behavior* ::destroy-sub-tree
                  :trigger #{:destroy}
                  :reaction (fn [this]
                              (doseq [f (concat (:files @this) (:folders @this))]
                                (object/destroy! f))))

(defui file-toggle [this]
  [:p (bound this #(files/basename (:path @this)))]
  :contextmenu (fn [e]
                 (object/raise this :menu! e)
                 (dom/prevent e)
                 (dom/stop-propagation e))
  :click (fn []
           (object/raise this :open!)))

(defui folder-toggle [this]
  [:p.folder (bound this #(str (files/basename (:path @this)) files/separator))]
  :contextmenu (fn [e]
                 (object/raise this :menu! e)
                 (dom/prevent e)
                 (dom/stop-propagation e))
  :click (fn []
           (if-not (:open? @this)
             (object/raise this :open!)
             (object/raise this :close!))))

(defui sub-folders [{:keys [folders files open? path root?]}]
  [:ul {:class (str (when-not root? "sub ")
                    (when open? "opened"))}
   (for [f (sort-by #(-> @% :path files/basename string/lower-case) folders)]
     (object/->content f))
   (for [f (sort-by #(-> @% :path files/basename string/lower-case) files)]
     (object/->content f))])

(defui rename-input [this]
  [:input.rename {:type "text" :value (files/basename (:path @this))}]
  :focus (fn []
           (object/raise this :rename.focus))
  :blur (fn []
           (object/raise this :rename.blur)))

(defn renameable [this cur content]
  (if cur
    (rename-input this)
    content))

(object/object* ::workspace.file
                :tags #{:workspace.file :tree-item}
                :path ""
                :init (fn [this path]
                        (object/merge! this {:path path})
                        [:li {:class (bound this #(if (:renaming? %)
                                                      "renaming"
                                                      ""))}
                         (rename-input this)
                         [:div.tree-item
                          (file-toggle this)]]))

(object/object* ::workspace.folder
                :tags #{:workspace.folder :tree-item}
                :path ""
                :open? false
                :realized? false
                :folders []
                :files []
                :init (fn [this path]
                          (object/merge! this {:path path})
                          [:li {:class (bound this #(if (:renaming? %)
                                                      "renaming"
                                                      ""))}
                           (rename-input this)
                           [:div.tree-item
                            (when path
                              (folder-toggle this))
                            [:div
                             (bound this sub-folders)]]]))

(object/object* ::workspace.root
                :tags #{:workspace.root}
                :root? true
                :files []
                :folders []
                :open? true
                :init (fn [this]
                        [:div.tree-root
                         (bound this sub-folders)]))

(def tree (object/create ::workspace.root))
(object/tag-behaviors :tree-item [::rename-blur ::rename-focus ::rename-blur ::start-rename ::rename-submit ::rename-cancel])
(object/tag-behaviors :workspace [::on-ws-add ::on-ws-remove ::on-ws-set ::watched.delete ::watched.create])
(object/tag-behaviors :workspace.file [::on-open-file ::subfile-menu ::on-menu ::delete-file ::rename-file])
(object/tag-behaviors :workspace.folder [::destroy-sub-tree ::on-open-ls ::on-close ::refresh ::subfolder-menu ::on-menu ::delete-folder ::new-file! ::force-delete-folder ::rename-folder ::new-folder!])
(object/tag-behaviors :workspace.file.root [::on-root-menu])
(object/tag-behaviors :workspace.folder.root [::on-menu ::on-root-menu])
(object/tag-behaviors :workspace.root [::add-ws-folder ::on-clear ::add-ws-file ::on-remove])

(defui input [type event]
  [:input {:type "file" type true :style "display:none;"}]
  :change (fn []
            (this-as me
                     (when-not (empty? (dom/val me))
                       (object/raise tree event (dom/val me))))))

(defn open-folder []
  (set! active-dialog (input :nwdirectory :workspace.add.folder!))
  (dom/trigger active-dialog :click))

(defn open-file []
  (set! active-dialog (input :blah :workspace.add.file!))
  (dom/trigger active-dialog :click))

(defui button [name action]
  [:li name]
  :click action)

(defn recent [this]
  (object/raise this :recent!))

(defui recents-item [this r]
  [:li
   [:ul.folders
    (for [f (:folders r)]
      [:li (files/basename f) files/separator])]
   [:ul.files
    (for [f (:files r)]
      [:li (files/basename f)])]]
  :click (fn []
           (object/raise this :recent.select! r)))

(defui recents [this rs]
  [:div
   (back-button this)
   [:ul
    (for [r rs]
      (recents-item this r))]])

(defui back-button [this]
  [:h2 "Select a workspace"]
  :click (fn []
           (object/raise this :tree!)))

(object/behavior* ::recent!
                  :triggers #{:recent!}
                  :reaction (fn [this]
                              (object/merge! this {:recents (recents this (workspace/all))})
                              ))

(object/behavior* ::tree!
                  :triggers #{:tree!}
                  :reaction (fn [this]
                              (object/merge! this {:recents nil})
                              ))

(object/behavior* ::recent.select!
                  :triggers #{:recent.select!}
                  :reaction (fn [this sel]
                              (workspace/open workspace/current-ws (:path sel))
                              (object/raise this :tree!)
                              ))

(defn ws-class [ws]
  (str "workspace" (when (:recents ws)
                     " recents")))

(defui workspace-ui [this]
  [:div {:class (bound this ws-class)}
   [:div.wstree
    [:ul.buttons
     ;[:li.sep "Open:"]
     (button "folder" open-folder)
     [:li.sep "|"]
     (button "file" open-file)
     [:li.sep "|"]
     (button "recent" #(recent this))
     ]
   [:ul.root
    (object/->content tree)]]
   [:div.recent
    (bound this :recents)
    ]]
  :contextmenu (fn [e]
                 (object/raise this :menu! e)))

(object/object* ::sidebar.workspace
                :tags #{:sidebar.workspace}
                :label "workspace"
                :order -7
                :init (fn [this]
                        (workspace-ui this)
                        ))

;(dom/trigger (input) :click)
(object/behavior* ::sidebar-menu
                  :triggers #{:menu!}
                  :reaction (fn [this e]
                              (-> (menu [{:label "Add folder"
                                          :click (fn [] (cmd/exec! :workspace.add-folder))}
                                         {:label "Add file"
                                          :click (fn [] (cmd/exec! :workspace.add-file))}
                                         {:type "separator"}
                                         {:label "Clear workspace"
                                          :click (fn [] (object/raise tree :clear!))}])
                                  (show-menu (.-clientX e) (.-clientY e)))
                              ))

(object/tag-behaviors :sidebar.workspace [::sidebar-menu ::recent! ::tree! ::recent.select!])

(def sidebar-workspace (object/create ::sidebar.workspace))

(sidebar/add-item sidebar/sidebar sidebar-workspace)

(cmd/command {:command :workspace.add-folder
              :desc "Workspace: add folder"
              :exec (fn []
                      (open-folder))})

(cmd/command {:command :workspace.add-file
              :desc "Workspace: add file"
              :exec (fn []
                      (open-file))})

(cmd/command {:command :workspace.show
              :desc "Workspace: Toggle workspace tree"
              :exec (fn []
                      (object/raise sidebar/sidebar :toggle sidebar-workspace {:transient? false}))})

(cmd/command {:command :workspace.rename.cancel!
              :desc "Workspace: Cancel rename"
              :exec (fn []
                      (when-let [c (ctx/->obj :tree.rename)]
                        (object/raise c :rename.cancel!)))})

(cmd/command {:command :workspace.rename.submit!
              :desc "Workspace: Submit rename"
              :exec (fn []
                      (when-let [c (ctx/->obj :tree.rename)]
                        (object/raise c :rename.submit!)))})