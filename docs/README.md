# Flex CLI

## Development

Use `.env.edn` file to set `"FLEX_API_BASE_URL"` to point to different environments (local, staging, prod ... )
Set `:api-key` in `~/.config/flex-cli/auth.edn` file to quickly change API key (or use `flex-cli login`)

### Development using Cursive Emacs & Cider

1. Create .env.end file. 

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

Now your dev environment is up and running! You can evaluate code in
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

### Development using Cursive

For REPL configuration, refer to:
https://shadow-cljs.github.io/docs/UsersGuide.html#_cursive
https://gist.github.com/akovantsev/44e2a0e10908785d1f40d3d3bcfff574#file-cursive-shadow-cljs-repl-setup-md

```bash
yarn shadow-cljs watch :dev
```

```clojure
(shadow/repl :dev)
```

In another terminal,

```bash
yarn run dev
```

Now, you can use cursive REPL! 

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
flex-cli
```

**Troubleshooting:** If nothing happens, press Ctrl+C. You probably
ran the other [flex command line
tool](https://github.com/westes/flex). Restart your terminal and try
again.

## Testing NPM publishing

You can use [Verdaccio](https://verdaccio.org/), a lightweight private
NPM registry to test the publish/install/update process with real NPM
registry.

See the [installation
instructions](https://verdaccio.org/docs/en/installation.html).

After you've installed it and started verdaccio server, you can
publish flex-cli to it, e.g.:

``` bash
npm install -g flex-cli --registry http://localhost:4873
```

**Tip:** In case you want to test installation with other machines in
your network, do this:

1. Check your IP address in the network (starts with 192.)
2. Start verdaccio server with `-l` option, e.g:

    ```bash
    verdaccio -l 192.168.1.91:4873
    ```

3. In the other machine, use that IP and port as the registry address
   when running `npm install`.

## Release to NPM

1. Change package `version` in `package.json` and in
   `src/sharetribe/flex_cli/cli_info.cljs`
1. Go to the repo root dir
1. Clean the build dir:

    ```bash
    yarn run clean
    ```

   The `target/` directory should be empty now.

1. Make a release build:

    ```bash
    yarn run release
    ```

1. Commit and push

1. Login as `sharetribe` with `npm login`

   - check credentials from password manager

1. Publish with `npm publish`

1. Add a new tag

    ```bash
    git tag -a v1.2.3 -m v1.2.3
    ```

1.  Update `latest` tag

    ```bash
    git push origin :refs/tags/latest
    git tag -f -a latest -m latest
    ```

1.  Push the tag

    ```bash
    git push --tags
    ```
    
1.  Announce the new version in Slack
