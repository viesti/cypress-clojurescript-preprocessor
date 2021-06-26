(ns net.tiuhti.cypress-cljs-test
  (:require [net.tiuhti.cypress-cljs :as sut]
            [cljs.test :refer [deftest is] :include-macros true]))

(deftest namespace-symbol
  (is (= (symbol "foo.bar.baz") (sut/namespace-symbol "foo/bar/baz.cljs")))
  (is (= (symbol "foo.bar.baz-foo") (sut/namespace-symbol "foo/bar/baz_foo.cljs"))))
