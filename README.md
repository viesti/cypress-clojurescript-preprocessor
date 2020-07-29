# cypress-clojurescript-preprocessor

ClojureScript preprocessor for Cypress

## Usage:

1. Create a project

   ```sh
   $ mkdir cypress-cljs-sample
   $ cd cypress-cljs-sample
   $ echo {} > package.json
   ```

2. Install [Cypress](https://docs.cypress.io/guides/getting-started/installing-cypress.html#Installing)

   ```sh
   $ npm install cypress --save-dev
   ```

3. Install cypress-clojurescript-preprocessor

   ```sh
   $ npm install cypress-clojurescript-preprocessor
   ```

4. Configure ClojureScript preprocessor

   1. Install  `@cypress/browserify-preprocessor` to keep the [default Browserify preprocessor](https://docs.cypress.io/api/plugins/preprocessors-api.html#Defaults)

      ```sh
       npm install --save-dev @cypress/browserify-preprocessor
      ```

   2. Add cypress-preprocessor-cljs to `cypress/plugins/index.js`

      ```sh
      $ mkdir -p cypress/plugins
      $ cat << EOF > cypress/plugins/index.js
      const makeCljsPreprocessor = require('cypress-clojurescript-preprocessor');
      // Create the default Browserify preprocessor for files other than *.cljs
      const makeBrowserifyPreprocessor = require('@cypress/browserify-preprocessor');
      /**
       * @type {Cypress.PluginConfig}
       */
      module.exports = (on, config) => {
        const browserifyPreprocessor = makeBrowserifyPreprocessor();
        const cljsPreprocessor = makeCljsPreprocessor(config);
        // Use the default Browserify preprocessor for files other than *.cljs
        on('file:preprocessor', (file) => file.filePath.endsWith('.cljs') ? cljsPreprocessor(file) : browserifyPreprocessor(file));
      };
      EOF
      ```
5. Write test in ClojureScript

   ```sh
   $ mkdir -p cypress/integration/examples
   $ cat << EOF > cypress/integration/examples/window.cljs
   (ns examples.window
     (:require-macros [latte.core :refer [describe beforeEach it]]))

   (def cy js/cy)

   (describe "Window"
     (beforeEach []
       (.visit cy "https://example.cypress.io/commands/window"))
     (it "cy.window() - get the global window object" []
       (.should (.window cy) "have.property" "top")))
   EOF
   ```
6. Run test

   ```sh
   $ ./node_modules/.bin/cypress open
   ```
