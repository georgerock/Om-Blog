(ns om-blog.core
    (:require [cljsjs.markdown :as markdown]
              [om.core :as om :include-macros true]
              [goog.events :as events]
              [om-bootstrap.button :as b]
              [om-bootstrap.modal :as md]
              [cljs.core.async :refer [put! chan <!]]
              [om.dom :as dom :include-macros true])
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:import [goog.net XhrIo]
             goog.net.EventType
             [goog.events EventType]))

(enable-console-print!)

(defn json-xhr [{:keys [method url data on-complete]}]
    (let [xhr (XhrIo.)]
        (events/listen xhr goog.net.EventType.COMPLETE
            (fn [e]
                (on-complete (js->clj (.getResponseJson xhr)))))
                (. xhr
                    (send url method (when data (clj->js data))
                    #js {"Content-Type" "application/json"}))))

(defn load-articles [url on-complete]
    (json-xhr
        {:method "GET"
         :url url
         :data nil
         :on-complete on-complete}))

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:articles []
                          :article-pages []
                          :page-number 1}))

(defn reload-articles [page-num channel]
    (load-articles (str "http://localhost:3000/blogposts?page=" page-num)
        (fn [res]
            (put! channel res))))

(defn trigger [article owner]
    (reify
        om/IInitState
        (init-state [_]
            {:visible? false :article []})
        om/IRender
        (render [_]
            (dom/div #{:className "modal in"}
                (md/modal {:header        (dom/h4 #js
                                            {:dangerouslySetInnerHTML #js
                                                {:__html (.toHTML js/markdown
                                                            (str (get (om/get-state owner :article) "title")))}})
                           :footer        (dom/div (b/button {} "Save")
                                                    (b/button {:on-click
                                                                (fn [_] (om/set-state! owner :visible? false))} "Close"))
                           :close-button? false
                           :visible?      (om/get-state owner :visible?)}
                                          (dom/div #{:className "container"}
                                            (dom/span #js {:dangerouslySetInnerHTML #js
                                                            {:__html
                                                                (.toHTML js/markdown
                                                                    (str (get (om/get-state owner :article) "body")))}})))
                                (b/button {:className "arrowLink arrowRight"
                                           :on-click (fn [_] (do (om/set-state! owner :visible? true)
                                                                 (load-articles (str "http://localhost:3000/blogposts/"
                                                                                    (str (get article "id")))
                                                                    (fn [res]
                                                                        (om/set-state! owner :article (js->clj res))))))}
                                    "Read Article")))))

(defn article [article owner]
    (reify
        om/IRenderState
        (render-state [state {:keys [current-page]}]
            (dom/div #js {:className "col-sm-4 article_container"}
                (dom/article #js {:id (str (get article "id"))}
                (apply dom/div #js {:className "article_content"}
                    [(dom/h3 nil (get article "title"))
                    (dom/div #js {:dangerouslySetInnerHTML #js
                                 {:__html (.toHTML js/markdown
                                            (str (get article "body")))}})
                    (om/build trigger article)]))))))

(defn page-bar [state owner]
    (reify
        om/IRenderState
        (render-state [this {:keys [current-page]}]
            (dom/div #js {:className "col-sm-12"}
                (dom/nav nil
                    (map
                        #(dom/button #js {:type "button"
                                          :className "pagination"
                                          :onClick (fn [] (reload-articles (- % 1) current-page))} % )
                            (range 1 (+ 1 (:article-pages state)))))))))


(defn get-clear-left [res num-art]
    (let [id-num (take-nth 3 (range num-art))
          articles (get res "articles")
          clear-left (map #(str (get (nth articles %) "id")) id-num)]
        clear-left))

(defn articles [state owner]
    (reify
        om/IInitState
        (init-state [_]
            {:current-page (chan)})
        om/IRenderState
        (render-state [this {:keys [current-page]}]
            (dom/div #js {:id "clear"}
            (apply dom/ul nil (om/build-all article (:articles state)
                            {:init-state {:current-page current-page}}))
            (om/build page-bar state
                            {:init-state {:current-page current-page}})))
        om/IWillMount
        (will-mount [_]
            (do
                (let [current-page (om/get-state owner :current-page)]
                (go (loop []
                    (let [change (<! current-page)]
                        (if (= (:type change) "single")
                            (om/transact! state :current-page (fn [] (:value change)))
                            (om/transact! state :articles (fn [] (get change "articles")))
                            ))(recur))))
                (load-articles
                    (str "http://localhost:3000/blogposts?page=" (- (:page-number state) 1))
                        (fn [res]
                            (do
                                (.log js/console (str (get-clear-left res (count (:articles state)))))
                                (om/transact! state :articles (fn [] (get res "articles")))
                                (let [num-pages (/ (count (:articles state)) 5)]
                                (om/transact! state :article-pages (fn [] num-pages))))))))))


(defn main-page [state owner]
    (reify
        om/IRender
        (render [_]
            (dom/div #js {:id "content"
                          :className ""}
            (dom/div nil
                    (dom/div nil (om/build articles state)))))))

(om/root main-page app-state
  {:target (. js/document (getElementById "app-blog"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
