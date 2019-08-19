import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.random.Random

//用户及对应分支对象
var userAndBranches = listOf(
    UserAndBranch("GhostedProgramer", "P@ssw0rd05060335", "cjh1"),
    UserAndBranch("yyz2ha", "P@ssw0rd05060335", "yyz")
)

class UserAndBranch(
    var username: String,
    var password: String,
    var branchName: String
)

//要合并到的分支名
var mergeBranch = "master"
//要clone到的本地路径
var localPath = "E:\\Person document\\IdeaProjects\\MyGitHub\\nativeTest"
//git 头名称
var commandPrefix = listOf("Add", "Remove", "Delete", "Upgrade", "Fix", "Modify", "Merge", "Optimize", "Refactor")
//要提交的文件所在文件夹的路径
var filesPath = "E:\\Person document\\IdeaProjects\\MyGitHub\\nativeTest\\somfiles"
//要提交文件加相对当前文件夹的路径
var filesPrefix = "somefiles\\"
//起始时间yyyy-mm-dd HH:mm:ss
var startTime: String = "2019-07-02 20:00:00"
//终止时间yyyy-mm-dd HH:mm:ss
var endTime: String = "2019-08-08 20:00:00"


@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun main() {
    val dir = File(filesPath)
    val files = dir.listFiles().toMutableList().also {
        println("本次共提交文件数量为${it.size}")
    }
    while (files.size > 1) {
        val userAndBranch = userAndBranches.random()
        ExecTest.printMessage(" git config --global user.name ${userAndBranch.username}")
//        ExecTest.printMessage(" cmd /c date 2008-08-08")
        ExecTest.printMessage(" git checkout ${userAndBranch.branchName}")
        val randomFile = files[Random.nextInt(files.size - 1)]
        ExecTest.printMessage(" git commit $filesPrefix\\${randomFile.name} -m ''${commandPrefix.random()} ${randomFile.nameWithoutExtension}'")
        files.remove(randomFile)
        ExecTest.printMessage(" git checkout $mergeBranch")
        ExecTest.printMessage(" git merge ${userAndBranch.branchName}")
    }
}

class ExecTest {
    companion object {
        fun printMessage(input: String) {
            val process = Runtime.getRuntime().exec(input)
            val outputStream = process.inputStream
            val inputStreamReader = InputStreamReader(outputStream, "gbk")
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String?
            var flag = false
            do {
                line = bufferedReader.readLine()
                if (flag) {
                    println(line)
                }
                flag = true
            } while (line != null)
            process.waitFor()
            println("$input 执行结果为exitValue = ${process.exitValue()}")
        }
    }
}