(ns status-im.accounts.recover.styles
  (:require [status-im.components.styles :refer [color-white]]
            [status-im.utils.platform :refer [ios?]]))

(def screen-container
  {:flex             1
   :background-color color-white})

(def passphrase-input-max-height
  (if ios? 78 72))
