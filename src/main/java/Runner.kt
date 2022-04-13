import com.android.ide.common.vectordrawable.Svg2Vector
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
    Runner.main(
        args = arrayOf(
            "/Users/aleknazarov/StudioProjects/sportmaster/flexo-icons/"
        )
    )

//    SvgToVectorDrawableConverter.convertSvgToXml(
//        File("/Users/aleknazarov/IdeaProjects/sportmaster/svg-to-vectordrawable/142n-1w-16s.svg"),
//        File("/Users/aleknazarov/IdeaProjects/sportmaster/svg-to-vectordrawable/ic_eye_one.xml")
//    )
}

object Runner {

    private val runtimeFolder: String
        get() = formatFolderPath(Runner::class.java.protectionDomain.codeSource.location.path)

    @JvmStatic
    fun main(args: Array<String>) {
        val projectFolderPath = formatFolderPath(args.getOrNull(0).orEmpty())
//            .let { it.ifEmpty { runtimeFolder } }
        val destFolderPath = formatFolderPath(
            args.getOrNull(1).orEmpty()
                .trim()
//            .let { if (!it.startsWith(File.separator)) runtimeFolder + it else it }
                .let { it.ifEmpty { projectFolderPath + "xml" } }
        )
        if (projectFolderPath.isBlank()) {
            println("Project folder doesn't set")
            exitProcess(-1)
        }
        if (destFolderPath.isBlank()) {
            println("Destination folder doesn't set")
            exitProcess(-1)
        }
        if (Files.exists(Path.of(destFolderPath))
            && !Files.isDirectory(Path.of(destFolderPath))
        ) {
            println("Destination folder isn't directory : $destFolderPath")
            exitProcess(-1)
        }
        val projectFolder = File(projectFolderPath)
        if (!projectFolder.isDirectory) {
            println("Project folder isn't directory : $projectFolderPath")
            exitProcess(-1)
        }
        val projectFiles = projectFolder.listFiles().orEmpty()

        if (projectFiles.none { it.name == "svg" && it.isDirectory }) {
            println("Project folder doesn't contain svg folder $projectFolder")
            exitProcess(-1)
        }
        if (projectFiles.none { it.name == "README.md" && it.isFile }) {
            println("Project folder doesn't contain README.md file")
            exitProcess(-1)
        }

        val sourceFolderPath = formatFolderPath(projectFolderPath + "svg")
        val renameIdToAliasMappingFilePath = projectFolderPath + "README.md"
        val renameFileMap = buildRenameFileMap(renameIdToAliasMappingFilePath)
        println("Source: $sourceFolderPath")
        println("Destination: $destFolderPath")
        println("Rename from: $renameIdToAliasMappingFilePath")
        println("Rename mapping: ${renameFileMap.size}")
        print("Start? - (y/n): ")
        val answer = readln()
        if (answer.isNotEmpty() && answer != "y") {
            exitProcess(-1)
        }

        SvgToVectorDrawableConverter.convert(
            sourceFolderPath = sourceFolderPath,
            destFolderPath = destFolderPath,
            renameFileMap = renameFileMap
        )

        println("Complete")
        println()
        println("Copy converted xml files to android project?(y/n)")
        val copyAnswer = readln()
        if (copyAnswer.isNotEmpty() && copyAnswer != "y") {
            exitProcess(-1)
        }
        println("Specify destination directory: ")
        val copyDestDirPath = formatFolderPath(readln())
        if (copyDestDirPath.isEmpty() || copyDestDirPath.isBlank()) {
            exitProcess(-1)
        }

        SvgToVectorDrawableConverter.copyXmlFilesWithOverwrite(destFolderPath, copyDestDirPath)
        println("Copy complete")
    }


    private fun buildRenameFileMap(file: String): Map<String, String> {
        val prefix = "![icon]"

        val map = mutableMapOf<String, String>()
        for (line in File(file).readLines()) {
            if (!line.startsWith(prefix)) continue

            val relativeFilePath = line.substringAfter(prefix)
                .substringAfter("(")
                .substringBefore(")")

            val iconAlias = line.substringAfter("|").substringBefore("|").trim()

            map[relativeFilePath] = iconAlias

        }
        return map
    }
}

object SvgToVectorDrawableConverter {
    private const val ICON_PREFIX = "ic_"
    private const val SVG_SUFFIX = "svg"
    private const val XML_SUFFIX = "xml"

    fun convert(sourceFolderPath: String, destFolderPath: String, renameFileMap: Map<String, String>) {

        scanSvgFiles(sourceFolderPath) { file ->
            val folder = formatFolderPath(file.parentFile.absolutePath.replace(sourceFolderPath, ""))

            convertSvgToXml(
                sourceFile = file,
                targetFile = File(
                    destFolderPath
                            + folder
                            + buildXmlFileName(file, renameFileMap)
                            + "." + XML_SUFFIX
                )
            )
        }
    }


    private fun buildXmlFileName(file: File, renameFileMap: Map<String, String>): String {
        val name = file.name

        val nameBuilder = StringBuilder()
        if (!name.startsWith(ICON_PREFIX)) {
            nameBuilder.append(ICON_PREFIX)
        }
        nameBuilder.append(
            renameFileMap.entries.find { it.key.endsWith(name) }
                ?.let { formatName(it.value) }
                .also { if (it == null) println("   !!! Alias not found $name") }
                ?: name
        )
        return nameBuilder.toString()
    }

    private fun formatName(name: String): String {
        return name.replace("-", "_")
    }

    private fun convertSvgToXml(sourceFile: File, targetFile: File) {
        println("convert ${sourceFile.name} to ${targetFile.name} folder: ${targetFile.parent}")
        if (!targetFile.exists()) {
            val parentFile = targetFile.parentFile
            if (!parentFile.exists()) {
                if (!parentFile.mkdirs()) {
                    println("Folder not created $parentFile")
                    return
                }
            }
            if (!targetFile.createNewFile()) {
                println("File not created $targetFile")
                return
            }
        }
        FileOutputStream(targetFile).use { fous ->
            Svg2Vector.parseSvgToXml(sourceFile, fous);
        }
    }

    private fun scanSvgFiles(sourceFolderPath: String, onSvgFile: (File) -> Unit) {
        val file = File(sourceFolderPath)
        scan(file, SVG_SUFFIX, onSvgFile)
    }

    private fun scanXmlFiles(sourceFolderPath: String, onSvgFile: (File) -> Unit) {
        val file = File(sourceFolderPath)
        scan(file, XML_SUFFIX, onSvgFile)
    }

    private fun scan(file: File, extension: String, onFile: (File) -> Unit) {
        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach { scan(it, extension, onFile) }
        } else if (file.extension == extension) {
            onFile(file)
        }
    }

    fun copyXmlFilesWithOverwrite(sourceFolderPath: String, destFolderPath: String) {
        scanXmlFiles(sourceFolderPath) { file ->
            file.copyTo(
                target = File(destFolderPath + file.name),
                overwrite = true
            )

            println("${file.name} copied to $destFolderPath")
        }
    }


}

private fun formatFolderPath(path: String): String {
    return if (path.endsWith(File.separator)) path else path + File.separator
}

fun m2ain() {
    val targetFile = File("/Users/aleknazarov/IdeaProjects/sportmaster/svg-to-vectordrawable/test-result.xml")
    val fous = FileOutputStream(targetFile)
    val source = File("/Users/aleknazarov/IdeaProjects/sportmaster/svg-to-vectordrawable/test.svg")
    Svg2Vector.parseSvgToXml(source, fous);
}