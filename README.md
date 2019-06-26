# Flex CLI

## Development

*These instructions are for Emacs & Cider.*

1. Create .env.end file

    ```bash
    cp .env.edn.tmpl .env.edn
    ```

1. In Emacs:

    ```
    > cider-jack-in-cljs
    > Select ClojureScript REPL type: shadow
    > Select shadow-cljs build: dev
    > Open browser? what ever
    ```

1. In terminal tab:

    ```
    > yarn install
    > node target/dev.js (or yarn run dev, which does take same thing)
    ```

Now your dev environment is up an running! You can evaluate code in
Emacs and Cider is connected to the running application.

To run the CLI with command-line arguments, evaluate in REPL:

``` clojure
(sharetribe.flex-cli.core/main-dev-str "process list -m bike-soil")
```

**Pro tip:**

To get rid of the annoying questions when running
`cider-jack-in-cljs`, copy the `.dir-locals.el` from template file:

```
> cp .dir-locals.el.tmpl .dir-locals.el
```

### Hot loading

The `dev` build is configured to do hot code loading.

When you run `node target/dev.js`, the process is left running for hot
loading purposes. You will see a log message when new code is loaded.

To run the CLI with new code, evaluate:

``` clojure
(sharetribe.flex-cli.core/main-dev-str "process list -m bike-soil")
```

## Running tests

### Run tests once

```bash
yarn run test
```

Return 0 or 1 exit code based on the result.

### Autorun tests

*in Emacs*

```
yarn run test-autorun
```

The autorun will keep running the tests when you change any file. See
the REPL for test output.

Unfortunately, running CLJS tests using Cider `C-c C-t t` is [not
supported](https://github.com/clojure-emacs/cider/issues/1268#issuecomment-492379163)

## Release

Compile release build:

```
yarn run compile
```

Run it:

```
node target/min.js <arguments>
```

## Install

To install the compiled release build:

```
yarn global add file:$(pwd)
```

Run it:

```
flex
```

**Troubleshooting:** If nothing happens, press Ctrl+C. You probably
ran the other [flex command line
tool](https://github.com/westes/flex). Restart your terminal and try
again.
