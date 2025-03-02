@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.application.extensions

import com.varabyte.kobweb.common.navigation.BasePath
import com.varabyte.kobweb.common.text.prefixIfNot
import com.varabyte.kobweb.gradle.application.Browser
import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import com.varabyte.kobweb.gradle.core.util.HtmlUtil
import com.varabyte.kobweb.project.KobwebFolder
import com.varabyte.kobweb.project.conf.KobwebConf
import kotlinx.html.HEAD
import kotlinx.html.consumers.filter
import kotlinx.html.link
import kotlinx.html.meta
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import java.nio.file.Path
import javax.inject.Inject
import kotlin.time.Duration

/**
 * A sub-block for defining all properties relevant to a Kobweb application.
 */
abstract class AppBlock @Inject constructor(
    kobwebFolder: KobwebFolder,
    conf: KobwebConf,
    baseGenDir: Property<String>,
) : KobwebBlock.FileGeneratingBlock {

    /**
     * A sub-block for defining properties related to the "index.html" document generated by Kobweb
     */
    abstract class IndexBlock @Inject constructor(@get:Internal val basePath: BasePath) : ExtensionAware {
        @Deprecated("`routePrefix` changed to `basePath` as that name is more consistent with what other web frameworks use.",
            ReplaceWith("basePath")
        )
        @get:Internal
        val routePrefix get() = basePath

        /**
         * A list of element builders to add to the `<head>` of the generated `index.html` file.
         *
         * You should normally use [ListProperty.add] to add new elements to the head block:
         * ```
         * kobweb.app.index.head.add {
         *    link(href = "styles.css", rel = "stylesheet")
         * }
         * ```
         * which will preserve the default entries added by Kobweb. Use [ListProperty.set] to override the defaults with
         * custom entries.
         */
        @get:Internal
        abstract val head: ListProperty<HEAD.() -> Unit>

        /** The serialized version of the [head] elements, intended for use as a Gradle task input. */
        @get:Input
        internal val serializedHead = head.map { list ->
            list.joinToString("") { HtmlUtil.serializeHeadContents(it) }
        }

        /**
         * The default description to set in the meta tag.
         *
         * Note that if you completely replace the head block (e.g. `head.set(...)` in your build script), this value
         * will not be used.
         */
        @get:Input
        abstract val description: Property<String>

        /**
         * The path to use for the favicon in the link tag.
         *
         * For example, "/favicon.ico" (which is the default value) will refer to the icon file located at
         * "jsMain/resources/public/favicon.ico".
         *
         * You are expected to begin your path with a '/' to explicitly indicate that the path will always be rooted
         * regardless of which URL on your site you visit. If you do not, a leading slash will be added for you.
         *
         * Note that if you completely replace the head block (e.g. `head.set(...)` in your build script), this value
         * will not be used.
         */
        @get:Input
        abstract val faviconPath: Property<String>

        /**
         * The language code to set in the html tag.
         *
         * Defaults to "en". You can set this to another language or even "" if you want to clear it.
         */
        @get:Input
        abstract val lang: Property<String>

        /**
         * A list of attribute key / value pairs to add to the script tag that imports your site.
         *
         * By default, Kobweb will just generate a very minimal script tag:
         *
         * ```
         * <script src="/yourapp.js"></script>
         * ```
         *
         * However, if you need to add attributes to it, you can do so here. For example, if you need to add a `type`
         * attribute, you can do so like this:
         *
         * ```
         * scriptAttributes.put("type", "module")
         * ```
         *
         * which would generate:
         *
         * ```
         * <script src="/yourapp.js" type="module"></script>
         * ```
         */
        @get:Input
        abstract val scriptAttributes: MapProperty<String, String>

        /**
         * A list of dependencies that should be excluded from generating html elements in the `index.html` file.
         *
         * Each entry in this list is a String prefix, which is checked to see if an actual dependency name begins with
         * this value.
         *
         * For example, if you wanted to block "some-library-js-1.0.klib", you could add the string
         * "some-library-js-1.0.klib" itself, or "some-library-js-1.0", or "some-library-js", or even just
         * "some-library".
         *
         * If a project ever includes a Kobweb library that declares a collection of head elements, this will be
         * reported with a message that shows what they are and includes instructions on using this method. An example
         * message looks like this:
         *
         * ```
         * Dependency "kotlin-bootstrap-js-1.0.klib" will add the following <head> elements to your site's index.html:
         *   <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"></script>
         *   <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css">
         *   <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css">
         * Add `kobweb { app { index { excludeHtmlForDependencies.add("kotlin-bootstrap") } } }` to your build.gradle.kts file to opt-out.
         * ```
         *
         * You can do a full opt-out by calling [excludeAllHtmlFromDependencies].
         *
         * If you intentionally don't want to exclude a library and don't want the warning to show up,
         * use [suppressHtmlWarningsForDependencies] instead.
         */
        @get:Input
        abstract val excludeHtmlForDependencies: ListProperty<String>

        /**
         * A list of dependencies that you don't want the "excludeHtmlForDependencies" warning to show up for.
         *
         * @see excludeHtmlForDependencies
         */
        @get:Input
        abstract val suppressHtmlWarningsForDependencies: ListProperty<String>

        init {
            description.convention("Powered by Kobweb")
            faviconPath.convention("/favicon.ico")
            lang.convention("en")

            head.set(listOf {
                meta {
                    name = "description"
                    content = description.get()
                }
                link {
                    rel = "icon"
                    href = basePath.prependTo(faviconPath.get().prefixIfNot("/"))
                }

                // Viewport content chosen for a good mobile experience.
                // See also: https://developer.mozilla.org/en-US/docs/Web/HTML/Viewport_meta_tag#viewport_basics
                meta("viewport", "width=device-width, initial-scale=1")
            })
        }
    }

    /**
     * Configuration values for the backend of this Kobweb application.
     */
    abstract class ServerBlock : ExtensionAware {
        /**
         * Configuration for remote debugging.
         */
        abstract class RemoteDebuggingBlock : ExtensionAware {
            /**
             * When `true`, enables remote debugging on the Kobweb server.
             *
             * Remote debugging will only work if the server is running in development mode.
             */
            abstract val enabled: Property<Boolean>

            /**
             * The port to use for remote debugging.
             *
             * Defaults to `5005`, a common default for remote debugging.
             *
             * @see <a href="https://www.jetbrains.com/help/idea/attaching-to-local-process.html#attach-to-remote">Remote debugging documentation</a>
             */
            abstract val port: Property<Int>

            init {
                enabled.convention(false)
                port.convention(5005)
            }
        }

        init {
            extensions.create<RemoteDebuggingBlock>("remoteDebugging")
        }
    }

    /**
     * A sub-block for defining properties related to configuring the `kobweb export` step.
     */
    abstract class ExportBlock @Inject constructor(
        private val kobwebFolder: KobwebFolder,
    ) {
        /**
         * @property route The route for the current page being exported, including a leading slash
         *   (e.g. "/admin/login"). Note that if your project specifies a global base path, it will not be included
         *   here.
         */
        class ExportFilterContext(
            val route: String,
        )

        /**
         * Configuration values for taking a trace at the export step.
         *
         * You can read more about traces [in the official documentation](https://playwright.dev/docs/trace-viewer).
         *
         * @property root The root directory where traces should be saved.
         * @property filter A filter which, if set, will be invoked for every route being exported to test if it should
         *   be traced. For example, to export all traces under the /admin/ route, you could set this to
         *   `filter = { route -> route.startsWith("/admin/") }`.
         * @property includeScreenshots Whether to include screenshots in the final trace. This will increase the size
         *  of each trace, but it's highly encouraged to set this to true as it can be very helpful in debugging.
         *
         * @see enableTraces
         */
        class TraceConfig(
            val root: Path,
            val filter: (String) -> Boolean,
            val includeScreenshots: Boolean,
        )

        /**
         * Configuration for exporting a specific route.
         *
         * @property route The route to export (e.g. "/admin/login"). Your route should start with a leading slash, but
         *   one will be added for you if it's missing.
         * @property exportPath The path to export the route to (e.g. "admin/login.html"). If not specified, the
         *   default value will be the route appended with `.html` (or `index.html` if the route ends with a trailing
         *   slash). Unlike route, which should be absolute, the path should be relative. However, if you do specify a
         *   leading slash, it will be removed.
         */
        internal class RouteConfig(
            route: String,
            exportPath: String? = null,
        ) {
            val route = route.prefixIfNot("/")

            // Note: We drop any leading slash so we don't confuse File resolve logic
            val exportPath = (exportPath ?: this.route.let { route ->
                route.substringBeforeLast('/') + "/" +
                    (route.substringAfterLast('/').takeIf { it.isNotEmpty() } ?: "index") +
                    ".html"
            }).removePrefix("/")
        }

        /**
         * Which browser to use for the export step.
         *
         * Besides potentially affecting the snapshotted output and export times, this can also affect the download size.
         *
         * Chromium is chosen as a default due to its ubiquity, but Firefox may also be a good choice as its download size
         * is significantly smaller than Chromium's.
         */
        abstract val browser: Property<Browser>

        /**
         * Whether to include a source map when exporting your site.
         *
         * In release mode, source gets minified and obfuscated, but if you include your source map with your site, browsers
         * can still show you the original source code when you inspect an element.
         *
         * By default, this value is set to true, making it easier for developers to debug a problem gone awry.
         */
        abstract val includeSourceMap: Property<Boolean>

        /**
         * A filter which, if set, will be invoked for every page to test if it should be exported.
         *
         * The callback should return true to allow the export and false otherwise.
         *
         * If this isn't set, then all discovered pages will be exported.
         *
         * In general, it's important to have an exported file for every page you want to have SEO for. Let's say we
         * want to exclude some admin pages, as they won't need SEO, as this would reduce export time and the final disk
         * size needed for our site:
         *
         * ```kotlin
         * filter.set { !route.startsWith("/admin/") }
         * ```
         *
         * @see ExportFilterContext
         */
        abstract val filter: Property<ExportFilterContext.() -> Boolean>

        internal abstract val extraRoutes: SetProperty<RouteConfig>

        /**
         * Add a route to export on top of what's normally discovered.
         *
         * This can be useful if you want to export a specific dynamic route (which are normally skipped) or re-export
         * a copy of an existing route to a different location.
         *
         * Note that any added [filter] will NOT be applied to these routes.
         *
         * @param exportPath The final path to export the route to, including a trailing extension
         *   (e.g. "settings/admin.html"). If not specified, it will be auto-determined from the route.
         */
        fun addExtraRoute(route: String, exportPath: String? = null) {
            extraRoutes.add(RouteConfig(route, exportPath))
        }

        /**
         * The max timeout to allow for each export.
         *
         * By default, this is chosen by Playwright, which at the time of writing this documentation uses 30 seconds as
         * a timeout.
         */
        abstract val timeout: Property<Duration>

        /**
         * @see enableTraces
         */
        internal abstract val traceConfig: Property<TraceConfig>

        /**
         * Force the static export task to always copy files when supporting legacy route redirecting.
         *
         * Normally, the export task tries to use symbolic links if it can, as it is a more elegant, efficient solution.
         *
         * However, there may be static hosting providers that don't support symbolic links, so this option is provided
         * to force copying instead.
         */
        abstract val forceCopyingForRedirects: Property<Boolean>

        /**
         * Enable traces for your export.
         *
         * Traces are a feature provided by Playwright (the engine we use to download a browser and take export
         * snapshots). Traces should rarely be required, but they can help you understand what's going on under the hood
         * when an export takes significantly longer than you'd expect.
         *
         * When enabled, a bunch of zip files will be saved under your specified trace path, which can be dragged /
         * dropped into the [Playwright Trace Viewer](https://trace.playwright.dev/) to get a breakdown of what's going
         * on. This can be useful to do in combination with setting the server logs level to "TRACE" (and then checking
         * `.kobweb/server/logs/kobweb-server.log` to get a deeper look into what's gone wrong.)
         *
         * You can read more about traces [in the official documentation](https://playwright.dev/docs/trace-viewer).
         *
         * See the docs for [TraceConfig] for more details about the parameters for this method. The `tracesRoot`
         * location defaults to `.kobweb/export-traces`.
         *
         * @see com.varabyte.kobweb.project.conf.Server.Logging.Level
         */
        fun enableTraces(
            tracesRoot: Path = kobwebFolder.path.resolve("export-traces"),
            filter: (String) -> Boolean = { true },
            showScreenshots: Boolean = true,
        ) {
            traceConfig.set(TraceConfig(tracesRoot, filter, showScreenshots))
            traceConfig.disallowChanges()
        }

        init {
            browser.convention(Browser.Chromium)
            includeSourceMap.convention(true)
            forceCopyingForRedirects.convention(false)
        }
    }

    /**
     * A collection of key / value pairs which will be made available within your Kobweb app via `AppGlobals`.
     *
     * This is a useful place to save constant values that describe your app, like a version value or build timestamp.
     *
     * See also: `com.varabyte.kobweb.core.AppGlobals`.
     */
    abstract val globals: MapProperty<String, String>

    /**
     * When `true`, all URLs will have their `.htm` and `.html` suffix automatically removed when the user types it in.
     *
     * Defaults to `true`.
     */
    abstract val cleanUrls: Property<Boolean>

    /**
     * If set, add a prefix to all CSS names generated for this library.
     *
     * This applies to CssStyle and Keyframes properties.
     *
     * For example, if you are working on a bootstrap library and set the default prefix to "bs", then a property like
     * `val ButtonStyle = CssStyle { ... }` would generate a CSS classname `bs-button` instead of just `button`.
     *
     * NOTE: You can override prefixes on a case-by-case basis by setting the `@CssPrefix` annotation on the property
     * itself.
     *
     * If you are writing an app and simply refactoring it into pieces for organizational purposes, then you don't need
     * to set this. However, if you plan to publish your library for others to use, then setting a prefix is a good
     * practice to reduce the chance of name collisions for when they are defining their own styles.
     */
    @get:Input
    abstract val cssPrefix: Property<String>

    /**
     * The strategy for whether to allow flexibility around supporting legacy route formats.
     *
     * When Kobweb was first released, it used very simple strategies for generating routes from your Kotlin project:
     * - filenames would be lowercased
     * - packages were converted into routes as is
     *
     * This is fine for most sites, where filenames and packages are usually single words. But this was problematic for
     * multi-word scenarios.
     *
     * For example, if you have a page called "StateOfArt.kt", this creates "stateofart"... where users would likely
     * prefer "state-of-art" instead, both for clarity and to avoid its flatulence-adjacent misreading.
     *
     * Hyphens are pretty common in clean URLs, so Kobweb has changed to support them as the default behavior. However,
     * this leaves users of old sites in a bit of a pickle. If they have built up links and SEO around the old link
     * formats, forcing them to change overnight could be a bit of a problem.
     *
     * Therefore, for older sites, allowing the old formats to continue to work is probably a safe idea, during which
     * time you may want to audit your links. Newer sites are encouraged to disallow these legacy redirects, since they
     * don't have to worry about potentially stale links in this case.
     *
     * If this property isn't set explicitly, it will default to [WARN] in development and [ALLOW] in production.
     */
    enum class LegacyRouteRedirectStrategy {
        ALLOW,
        WARN,
        DISALLOW,
    }

    /**
     * @see LegacyRouteRedirectStrategy
     */
    @Deprecated("This property is no longer used and should be removed.")
    abstract val legacyRouteRedirectStrategy: Property<LegacyRouteRedirectStrategy>

    init {
        globals.set(mapOf("title" to conf.site.title))
        cleanUrls.convention(true)
        genDir.convention(baseGenDir.map { "$it/app" })

        extensions.create<IndexBlock>("index", BasePath(conf.site.basePathOrRoutePrefix))
        extensions.create<ServerBlock>("server")
        extensions.create<ExportBlock>("export", kobwebFolder)
    }
}

val AppBlock.index: AppBlock.IndexBlock
    get() = extensions.getByType<AppBlock.IndexBlock>()

val AppBlock.export: AppBlock.ExportBlock
    get() = extensions.getByType<AppBlock.ExportBlock>()

val AppBlock.server: AppBlock.ServerBlock
    get() = extensions.getByType<AppBlock.ServerBlock>()

val AppBlock.ServerBlock.remoteDebugging: AppBlock.ServerBlock.RemoteDebuggingBlock
    get() = extensions.getByType<AppBlock.ServerBlock.RemoteDebuggingBlock>()

val KobwebBlock.app: AppBlock
    get() = extensions.getByType<AppBlock>()

internal fun KobwebBlock.createAppBlock(kobwebFolder: KobwebFolder, conf: KobwebConf): AppBlock {
    return extensions.create<AppBlock>("app", kobwebFolder, conf, baseGenDir)
}

fun AppBlock.IndexBlock.excludeAllHtmlFromDependencies() {
    excludeHtmlForDependencies.set(listOf(""))
    excludeHtmlForDependencies.disallowChanges()
}
