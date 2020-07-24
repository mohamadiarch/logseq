(ns frontend.components.widgets
  (:require [rum.core :as rum]
            [frontend.util :as util]
            [frontend.handler :as handler]
            [frontend.state :as state]
            [frontend.config :as config]
            [clojure.string :as string]
            [frontend.ui :as ui]
            [frontend.db :as db]
            [frontend.version :as version]
            [frontend.components.svg :as svg]))

(rum/defcs choose-preferred-format
  []
  [:div
   [:h1.title {:style {:margin-bottom "0.25rem"}}
    "What's your preferred mode?"]

   [:div.mt-4.ml-1
    (ui/button
      "Markdown"
      :on-click
      #(handler/set-preferred-format! :markdown))

    [:span.ml-2.mr-2 "-OR-"]

    (ui/button
      "Org Mode"
      :on-click
      #(handler/set-preferred-format! :org))]])

(rum/defcs set-personal-access-token <
  (rum/local "" ::token)
  [state]
  (when (state/logged?)
    (let [access-token (get state ::token)]
      [:div.p-8.flex.items-center.justify-center
       [:div.w-full.mx-auto
        [:div
         [:div
          [:h1.title
           "Set Github personal access token"]
          [:div.pl-1
           [:p
            "The token will be encrypted and stored in the browser localstorage."
            [:br]
            "The server will never store it."]
           [:div.mt-4.mb-4.relative.rounded-md.shadow-sm.max-w-xs
            [:input#repo.form-input.block.w-full.sm:text-sm.sm:leading-5
             {:on-change (fn [e]
                           (reset! access-token (util/evalue e)))}]]
           (ui/button
             "Submit"
             :on-click
             (fn []
               (when-not (string/blank? access-token)
                 (handler/set-github-token! @access-token))))]

          [:hr]

          [:div.flex.flex-row.admonitionblock.align-items {:class "tip"}
           [:div.pr-4.admonition-icon.flex.flex-col.justify-center
            {:title "Tip"}
            (svg/tip)]
           [:div.ml-4.text-lg
            [:a {:href "https://logseq.com/blog/faq"
                 :target "_blank"}
             "How to create a Github personal access token?"]]]]]]])))

(rum/defc sync-status < rum/reactive
  []
  (let [repo (state/get-current-repo)]
    (when-not (= repo config/local-repo)
      (let [git-status (state/sub [:git/status repo])
            pulling? (= :pulling git-status)
            pushing? (= :pushing git-status)
            status (state/sub [:repo/sync-status repo])
            status (remove (fn [[_ files]]
                             (empty? files))
                           status)
            synced? (empty? (apply concat (vals status)))
            last-pulled-at (db/sub-key-value repo :git/last-pulled-at)]
        [:div.flex-row.flex.items-center
         (when pushing?
           [:span.lds-dual-ring.mt-1])
         (ui/dropdown
          (fn [{:keys [toggle-fn]}]
            [:div.cursor.w-2.h-2.sync-status.mr-2
             {:class (if synced? "bg-green-600" "bg-orange-400")
              :style {:border-radius "50%"
                      :margin-top 2}
              :on-mouse-over toggle-fn}])
          (fn [{:keys [toggle-fn]}]
            [:div.p-2.rounded-md.shadow-xs.bg-base-3.flex.flex-col.sync-content
             (when synced?
               [:p "All local changes are synced!"])
             (when-not synced?
               [:div
                [:div.changes
                 (for [[k files] status]
                   [:div {:key (str "sync-" (name k))}
                    [:div.text-sm.font-bold (string/capitalize (name k))]
                    [:ul
                     (for [file files]
                       [:li {:key (str "sync-" file)}
                        file])]])]
                [:div.flex.flex-row.justify-between.align-items.mt-2
                 (ui/button "Push now"
                   :on-click (fn [] (state/set-state! :modal/git-commit-message true)))
                 (if pushing?
                   [:span.lds-dual-ring.mt-1])]])
             [:hr]
             [:div
              [:p {:style {:font-size 12}} "Last pulled at: "
               last-pulled-at]
              [:div.flex.flex-row.justify-between.align-items
               (ui/button "Pull now"
                 :on-click (fn [] (handler/pull-current-repo)))
               (if pulling?
                 [:span.lds-dual-ring.mt-1])]
              [:p.pt-2.text-sm.opacity-50
               "Version: " version/version]]]))]))))

(rum/defc repos < rum/reactive
  [head? on-click]
  (let [current-repo (state/sub :git/current-repo)
        logged? (state/logged?)
        local-repo? (= current-repo config/local-repo)
        get-repo-name-f (fn [repo]
                          (if head?
                            (db/get-repo-path repo)
                            (util/take-at-most (db/get-repo-name repo) 20)))]
    (when logged?
      (if current-repo
        (let [repos (state/sub [:me :repos])]
          (if (> (count repos) 1)
            (ui/dropdown-with-links
             (fn [{:keys [toggle-fn]}]
               [:a#repo-switch {:on-click toggle-fn}
                [:span (get-repo-name-f current-repo)]
                [:span.dropdown-caret.ml-1 {:style {:border-top-color "#6b7280"}}]])
             (mapv
              (fn [{:keys [id url]}]
                {:title (get-repo-name-f url)
                 :options {:on-click (fn []
                                       (state/set-current-repo! url)
                                       (when-not (= :draw (state/get-current-route))
                                         (handler/redirect! {:to :home}))
                                       (when on-click
                                         (on-click url)))}})
              (remove (fn [repo]
                        (= current-repo (:url repo)))
                      repos))
             {:modal-class (util/hiccup->class
                            "origin-top-right.absolute.left-0.mt-2.w-48.rounded-md.shadow-lg ")})
            (if local-repo?
              [:span (get-repo-name-f current-repo)]
              [:a
               {:href current-repo
                :target "_blank"}
               (get-repo-name-f current-repo)])))))))

(rum/defc add-repo < rum/reactive
  []
  (let [repo-url (state/sub :git/clone-repo)]
    [:div.p-8.flex.items-center.justify-center
     [:div.w-full.mx-auto
      [:div
       [:div
        [:h1.title.mb-1
         "Import your notes"]
        [:p "You can import your notes from a repo on Github."]
        [:div.mt-4.mb-2.relative.rounded-md.shadow-sm.max-w-xs
         [:input#repo.form-input.block.w-full.sm:text-sm.sm:leading-5
          {:autoFocus true
           :placeholder "https://github.com/username/repo"
           :on-change (fn [e]
                        (state/set-git-clone-repo! (util/evalue e)))}]]]]

      (ui/button
        "Clone"
        :on-click
        (fn []
          (when (string/starts-with? repo-url "https://github.com/")
            (let [repo-url (string/replace repo-url ".git" "")]
              (handler/clone-and-pull repo-url)
              (handler/redirect! {:to :home})))))

      ;; (when git-ask-private-grant?
      ;;   [:div
      ;;    [:hr]
      ;;    [:div
      ;;     [:h3.text-red-700.mb-2 "Git clone failed, it might be two reasons:"]
      ;;     [:ol
      ;;      [:li.mb-1 "Please check the repo link is correct."]
      ;;      [:li
      ;;       [:div.mb-1
      ;;        "You're cloning a "
      ;;        [:b "private"]
      ;;        " repo, we need your permission grants for that.
      ;;       We promise that our server will never store your github oauth token, it'll be stored securely and only in the "
      ;;        [:a.underline {:title "Which has a HttpOnly flag"}
      ;;         "browser cookie"]
      ;;        "."]
      ;;       [:a {:href "/auth/github_ask_repo_permission"}
      ;;        (ui/button "Grant us your private repo permission")]]]]])
      ]]))
