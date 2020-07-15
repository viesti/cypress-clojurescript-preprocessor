# cypress-preprocessor-cljs

ClojureScript preprocessor for Cypress

## Usage (TODO: add npm install)

1. Install [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html#_installation), globally for now:

   ```sh
   npm install -g shadow.cljs
   ```

2. Add cypress-preprocessor-cljs to `plugins/index.js`

   ```js
   const makeCljsPreprocessor = require('cypress-preprocessor-cljs');

   /**
    * @type {Cypress.PluginConfig}
    */
   module.exports = (on, config) => {
     // `on` is used to hook into various events Cypress emits
       // `config` is the resolved Cypress config
       on('file:preprocessor', makeCljsPreprocessor(config));
   };
   ```

3. Write test in ClojureScript

   ```clojure
   (ns examples.another)

   (def cy js/cy)

   (js/context "Window"
       (fn []
         (js/beforeEach
          (fn []
            (.visit cy "https://example.cypress.io/commands/window")))
         (js/it "cy.window() - get the global window object"
           (fn []
             (.should (.window cy) "have.property" "top")))))
   ```
