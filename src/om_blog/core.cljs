(ns om-blog.core
  (:require [om.core :as om :include-macros true]
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
                          :single-article []
                          :article-pages []
                          :page-number 1}))

(defn get-single-article [page-id channel]
    (load-articles (str "http://localhost:3000/blogposts/" page-id)
    (fn [res]
        (let [final-res {:type "single" :value res}]
        (put! channel final-res)))))


(defn reload-articles [page-num channel]
    (load-articles (str "http://localhost:3000/blogposts?page=" page-num)
    (fn [res]
        (put! channel res))))

(defn article [article owner]
  (reify
    om/IRenderState
    (render-state [state {:keys [current-page]}]
      (dom/li nil
        (apply dom/div nil
          [(dom/h2 nil (get article "title"))
           (dom/div nil (get article "body"))
           (dom/button #js {:type "button"
                            :class "singleArticleButton"
                            :onClick #(get-single-article
                                        (str (get article "id"))
                                        current-page)}
                            "Read More")])))))

(defn page-bar [state owner]
    (reify
        om/IRenderState
        (render-state [this {:keys [current-page]}]
            (dom/div #js {:className "container"}
                (dom/div #js {:id "pageBar"}
                (map
                    #(dom/button #js {:type "button"
                                      :className "pageBar"
                                      :onClick (fn [] (reload-articles (- % 1) current-page))} % )
                    (range 1 (+ 1 (:article-pages state)))))))))

(defn single-article [state owner]
    (reify
        om/IRender
        (render [_]
            (dom/div #js {:className "container"}
                (dom/h2 nil (get state "title"))
                (dom/span nil (get state "body"))))))

(defn articles [state owner]
  (reify
    om/IInitState
    (init-state [_]
        {:current-page (chan)})
    om/IRenderState
    (render-state [this {:keys [current-page]}]
        (dom/div #js {:className "container"}
        (om/build single-article (:current-page state))
        (om/build page-bar state
                           {:init-state {:current-page current-page}})
      (apply dom/ul nil (om/build-all article (:articles state)
                        {:init-state {:current-page current-page}}))))
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
                (.log js/console (str "http://localhost:3000/blogposts?page=" (- (:page-number state) 1)))
                (om/transact! state :articles (fn [] (get res "articles")))
                (let [num-pages (/ (count (:articles state)) 5)]
                    (om/transact! state :article-pages (fn [] num-pages))))))))))

(defn main-page [state owner]
    (reify
        om/IRender
        (render [_]
            (dom/div #js {:id "content"
                          :className ""}
            (dom/div #js {:className "container"}
                    (dom/h2 nil "Articles: ")
                    (dom/h6 nil (str (:page-number state))
                    (dom/div nil (om/build articles state))))))))

(om/root main-page app-state
  {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
