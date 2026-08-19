//
// This file holds several functions used to perform JSON parameter validation, help and summary rendering.
//
// Modified from NF-Core's template: https://github.com/nf-core/tools

import org.yaml.snakeyaml.Yaml
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import nextflow.extension.FilesEx
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption

class NextflowTool {
    //
    // Dump pipeline parameters in a json file
    //
    public static void dump_parameters(workflow, params) {
        def timestamp  = new java.util.Date().format( 'yyyy-MM-dd_HH-mm-ss')
        def filename   = "params_${timestamp}.json"
        def temp_pf    = new File(workflow.launchDir.toString(), ".${filename}")
        def jsonStr    = JsonOutput.toJson(params)
        temp_pf.text   = JsonOutput.prettyPrint(jsonStr)

        FilesEx.copyTo(temp_pf.toPath(), "${params.results_dir}/pipeline_info/params_${timestamp}.json")
        temp_pf.delete()
    }

    // print argument key, help text and type (default/required), if provided
    public static void printHelpMessageBlock(argumentKey, argumentBlock, indent, log) {
        log.info indent + "--" + argumentKey
        if (argumentBlock.containsKey("default")) {
            log.info indent + indent + "default: " + argumentBlock.default
        } else if (argumentBlock.containsKey("required")) {
            log.info indent + indent + "<required>"
        }
        log.info indent + indent + argumentBlock.help_text
    }

    public static void help_message(schema_path_list, monochrome_logs, log) {
        Map colors = logColours(monochrome_logs)
        def indent = "      "

        //build a single core schema by folding in each schema's params in list order:
        //a param found in a schema located further down schema_path_list overwrites the value
        //found for the same param in a schema located earlier in the list
        def core_params = [:]

        for (schema_path in schema_path_list) {
            def schema_in = new File(schema_path).text
            def json = new JsonSlurper().parseText(schema_in)

            json.params.each { group ->
                def core_group = core_params.computeIfAbsent(group.key) { [:] }
                group.value.each { param ->
                    core_group[param.key] = param.value
                }
            }
        }

        //print the merged core schema
        core_params.each { group ->
            log.info "${colors.purple} ${group.key} ${colors.reset}"
            group.value.each { param ->
                if (param.key.toString().contains('header')) {
                    if (param.value.title) {
                        log.info indent
                        log.info "${colors.red} ${param.value.title} ${colors.reset}"
                    }
                    log.info indent + param.value.subtext
                    log.info indent
                } else {
                    printHelpMessageBlock(param.key, param.value, indent, log)
                }
            }
            //put a line to seperate
            log.info dashedLine(monochrome_logs)
        }
    }

    //
    // Print pipeline summary on completion
    //
    public static void summary(workflow, params, log) {
        Map colors = logColours(params.monochrome_logs)
        String pipelineName = workflow.manifest.name

        if (workflow.success) {
            if (workflow.stats.ignoredCount == 0) {
                log.info "-${colors.purple}[$pipelineName]${colors.green} Pipeline completed successfully${colors.reset}-"
            } else {
                log.info "-${colors.purple}[$pipelineName]${colors.yellow} Pipeline completed successfully, but with ignored process(es)${colors.reset}-"
             }
        } else {
            log.info "-${colors.purple}[$pipelineName]${colors.red} Pipeline errored before completion${colors.reset}-"
        }

        // Additional summary statistics
        log.info "${colors.cyan}Summary statistics:${colors.reset}"
        log.info "${colors.cyan}- Successfully completed processes: ${workflow.stats.succeedCount}${colors.reset}"
        log.info "${colors.cyan}- Failed and retried processes: ${workflow.stats.failedCount}${colors.reset}"
        log.info "${colors.cyan}- Failed and ignored processes: ${workflow.stats.ignoredCount}${colors.reset}"
        log.info "${colors.cyan}- Total processes: ${workflow.stats.succeedCount + workflow.stats.ignoredCount + workflow.stats.failedCount}${colors.reset}"
    }

    //
    // ANSII Colours used for terminal logging
    //
    public static Map logColours(Boolean monochrome_logs) {
        Map colorcodes = [:]

        // Reset / Meta
        colorcodes['reset']      = monochrome_logs ? '' : "\033[0m"
        colorcodes['bold']       = monochrome_logs ? '' : "\033[1m"
        colorcodes['dim']        = monochrome_logs ? '' : "\033[2m"
        colorcodes['underlined'] = monochrome_logs ? '' : "\033[4m"
        colorcodes['blink']      = monochrome_logs ? '' : "\033[5m"
        colorcodes['reverse']    = monochrome_logs ? '' : "\033[7m"
        colorcodes['hidden']     = monochrome_logs ? '' : "\033[8m"

        // Regular Colors
        colorcodes['black']      = monochrome_logs ? '' : "\033[0;30m"
        colorcodes['red']        = monochrome_logs ? '' : "\033[0;31m"
        colorcodes['green']      = monochrome_logs ? '' : "\033[0;32m"
        colorcodes['yellow']     = monochrome_logs ? '' : "\033[0;33m"
        colorcodes['blue']       = monochrome_logs ? '' : "\033[0;34m"
        colorcodes['purple']     = monochrome_logs ? '' : "\033[0;35m"
        colorcodes['cyan']       = monochrome_logs ? '' : "\033[0;36m"
        colorcodes['white']      = monochrome_logs ? '' : "\033[0;37m"

        // Bold
        colorcodes['bblack']     = monochrome_logs ? '' : "\033[1;30m"
        colorcodes['bred']       = monochrome_logs ? '' : "\033[1;31m"
        colorcodes['bgreen']     = monochrome_logs ? '' : "\033[1;32m"
        colorcodes['byellow']    = monochrome_logs ? '' : "\033[1;33m"
        colorcodes['bblue']      = monochrome_logs ? '' : "\033[1;34m"
        colorcodes['bpurple']    = monochrome_logs ? '' : "\033[1;35m"
        colorcodes['bcyan']      = monochrome_logs ? '' : "\033[1;36m"
        colorcodes['bwhite']     = monochrome_logs ? '' : "\033[1;37m"

        // Underline
        colorcodes['ublack']     = monochrome_logs ? '' : "\033[4;30m"
        colorcodes['ured']       = monochrome_logs ? '' : "\033[4;31m"
        colorcodes['ugreen']     = monochrome_logs ? '' : "\033[4;32m"
        colorcodes['uyellow']    = monochrome_logs ? '' : "\033[4;33m"
        colorcodes['ublue']      = monochrome_logs ? '' : "\033[4;34m"
        colorcodes['upurple']    = monochrome_logs ? '' : "\033[4;35m"
        colorcodes['ucyan']      = monochrome_logs ? '' : "\033[4;36m"
        colorcodes['uwhite']     = monochrome_logs ? '' : "\033[4;37m"

        // High Intensity
        colorcodes['iblack']     = monochrome_logs ? '' : "\033[0;90m"
        colorcodes['ired']       = monochrome_logs ? '' : "\033[0;91m"
        colorcodes['igreen']     = monochrome_logs ? '' : "\033[0;92m"
        colorcodes['iyellow']    = monochrome_logs ? '' : "\033[0;93m"
        colorcodes['iblue']      = monochrome_logs ? '' : "\033[0;94m"
        colorcodes['ipurple']    = monochrome_logs ? '' : "\033[0;95m"
        colorcodes['icyan']      = monochrome_logs ? '' : "\033[0;96m"
        colorcodes['iwhite']     = monochrome_logs ? '' : "\033[0;97m"

        // Bold High Intensity
        colorcodes['biblack']    = monochrome_logs ? '' : "\033[1;90m"
        colorcodes['bired']      = monochrome_logs ? '' : "\033[1;91m"
        colorcodes['bigreen']    = monochrome_logs ? '' : "\033[1;92m"
        colorcodes['biyellow']   = monochrome_logs ? '' : "\033[1;93m"
        colorcodes['biblue']     = monochrome_logs ? '' : "\033[1;94m"
        colorcodes['bipurple']   = monochrome_logs ? '' : "\033[1;95m"
        colorcodes['bicyan']     = monochrome_logs ? '' : "\033[1;96m"
        colorcodes['biwhite']    = monochrome_logs ? '' : "\033[1;97m"

        return colorcodes
    }

    //
    // Does what is says on the tin
    //
    public static String dashedLine(monochrome_logs) {
        Map colors = logColours(monochrome_logs)
        return "-" + "${colors.dim}-${colors.reset}"*63 + "-"
    }
    
    //
    // pam-info logo
    //
    public static String logo(workflow, monochrome_logs) {
        Map colors = logColours(monochrome_logs)
        String.format(
            """\n
            ${dashedLine(monochrome_logs)}
            ${colors.blue} _____________________  ___     ____________   _________________ ${colors.reset}
            ${colors.blue} ___  __ \\__    |__   |/  /     ____  _/__  | / /__  ____/_  __ \\ ${colors.reset}
            ${colors.blue} __  /_/ /_  /| |_  /|_/ /_________  / __   |/ /__  /_   _  / / / ${colors.reset}
            ${colors.blue} _  ____/_  ___ |  /  / /_/_____/_/ /  _  /|  / _  __/   / /_/ / ${colors.reset}
            ${colors.blue} /_/     /_/  |_/_/  /_/        /___/  /_/ |_/  /_/      \\____/ ${colors.reset}                                         
            \n
            ${colors.purple}  ${workflow.manifest.name} ${workflow.manifest.version} ${colors.reset}
            ${dashedLine(monochrome_logs)}
            """.stripIndent()
        )
    }

    public static Map getCommandLineParams(String commandLine) {
        // Splitting on whitespace, assume no whitespace appears in param values
        def commandLineTokens = commandLine.split(/\s+/)
        def commandLineParams = [:]

        for (int i = 0; i < commandLineTokens.length; i++) {
            def token = commandLineTokens[i]
            if (token.startsWith('--')){
                def key = token.trim()
                def value = true
                // Assume max 1 value can be supplied per param (inherent constraint in nextflow's commandline parsing) 
                if (i + 1 < commandLineTokens.length && !commandLineTokens[i+1].startsWith('--')) {
                    value = commandLineTokens[i+1].trim()
                    i++   // move past next token as it's the value for the previous param
                    commandLineParams[key] = value
                }
            }
        }
        return commandLineParams
    }

    public static void commandLineParams(String commandLine, log, monochrome_logs) {
        def indent = "      "
        Map colors = logColours(monochrome_logs)
        def commandLineParams = getCommandLineParams(commandLine)

        if (commandLineParams.isEmpty()) {
            log.info "${colors.purple} No parameters supplied: running with defaults. ${colors.reset}"
        } else {
            log.info "${colors.purple} The following parameters were provided on the command line: ${colors.reset}" 
            log.info indent
            commandLineParams.each { key, value ->
                log.info indent + "${key}: ${value}"
                }
            }
        log.info indent //whitespace after params
    }

    /*
    Resolve a path (following symlinks if present) and delete the
    real target, only if it falls inside the given safe root. Allowing you to not have to worry
    about accidentally deleting files outside of your workflow workDir, for example.


    Takes:
     file     the Path to inspect - may be a symlink or a regular file
     safeRoot root directory the resolved target MUST fall under (e.g. workflow.workDir)
     log      a logger to use for warnings normal log namespace in nextflow
    Returns:
     true if a delete happened
     false if skipped (missing or outside safeRoot) with a warning printed to stderr
     incase you want to handle it differently if it fails this download
    */
    public static boolean safeDelete(Path file, Path safeRoot, log) {
        if (file == null || !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }

        Path root = safeRoot.toRealPath()
        Path real = file.toRealPath()

        if (!real.startsWith(root)) {
            log.warn " refusing to delete '${real}' - outside safe root '${root}'"
            return false
        }

        if (Files.isSymbolicLink(file)) {
            Files.deleteIfExists(real)   // the actual target
            Files.deleteIfExists(file)
        } else {
            Files.deleteIfExists(file)
        }
        return true
    }
}