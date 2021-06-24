# cypress-clojurescript-preprocessor

A [Cypress preprocessor](https://docs.cypress.io/api/plugins/preprocessors-api.html) for [ClojureScript](https://clojurescript.org/), which uses [Shadow CLJS](https://github.com/thheller/shadow-cljs) for processing Cypress tests written in ClojureScript.

The plugin works by inspecting the Cypress `integrationFolder` (by default, `cypress/integration`) for ClojureScript files (`*.cljs`, e.g. `cypress/integration/my_app/app_test.cljs` -> `(ns my-app.app-test)`) and generates a `shadow-cljs.edn` configuration file with a build for each test file. This configuration is then used to compile the tests into Javascript before submitting to the browser.

The shadow-cljs server is kept running while the Cypress runner is active. When a test is run, the test file is watched for changes and a recompile is done if the test file is changed and Cypress is notified to rerun the test. The first compile is a bit slow, but subsequent compiles are fast (the plugin uses shadow-cljs `compile` command, which defaults to [`optimizations: none`](https://shadow-cljs.github.io/docs/UsersGuide.html#Optimization)).

## Example usage

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

   ```sh
   $ mkdir -p cypress/plugins
   $ cat << EOF > cypress/plugins/index.js
   const makeCljsPreprocessor = require('cypress-clojurescript-preprocessor');
   /**
    * @type {Cypress.PluginConfig}
    */
   module.exports = (on, config) => {
     on('file:preprocessor', makeCljsPreprocessor(config));
   };
   EOF
   ```

   This will delegate files other than `*.cljs` to the [default Browserify preprocessor](https://docs.cypress.io/api/plugins/preprocessors-api.html#Defaults). If you need to run other preprocessors, then combine them with for example:

   ```js
   module.exports = (on, config) => {
     const browserifyPreprocessor = makeBrowserifyPreprocessor();
     const cljsPreprocessor = makeCljsPreprocessor(config);
     // Use the default Browserify preprocessor for files other than *.cljs
     on('file:preprocessor', (file) => file.filePath.endsWith('.cljs') ? cljsPreprocessor(file) : browserifyPreprocessor(file));
   };
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

## Configuration

### Shadow CLJS configuration override

The Shadow CLJS configuration used by the preprocessor may be overridden via a `shadow-cljs-override.edn` file, which is merged on top of the default configuration with [meta-merge](https://github.com/weavejester/meta-merge). By default, `[mocha-latte "0.1.2"]` and `[chai-latte "0.2.0"]` are included in the shadow-cljs.edn configuration used by the preprocessor.

## Changelog

* 0.1.5 Merge override config ontop final config, allows to add additional :source-paths
* 0.1.4 Bundle browserify preprocessor
* 0.1.3 Add shadow-cljs-override.edn
* 0.1.2 Bundle Bundle mocha-latte and chai-latte
* 0.1.0 Initial release
