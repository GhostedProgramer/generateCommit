import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.random.Random

//用户及对应分支对象
var userAndBranches = listOf(
    UserAndBranch("cjh", "cjh1", false),
    UserAndBranch("ywb", "yyz", false)
)

class UserAndBranch(
    var gitName: String,
    var branchName: String,
    var exist: Boolean
)

//要合并到的分支名
var mergeBranch = "master"
//要clone到的本地路径
var localPath = "E:\\PersonDocument\\IdeaProjects\\MyGitHub\\generateCommit"
//git 头名称
var commandPrefix = listOf("add", "remove", "delete", "upgrade", "fix", "modify", "merge", "optimize", "refactor")
//要提交的文件所在文件夹的路径
var filesPath = "E:\\PersonDocument\\IdeaProjects\\MyGitHub\\generateCommit\\somefiles"
//要提交文件加相对当前文件夹的路径
var filesPrefix = "somefiles\\"
//起始时间yyyy-mm-dd HH:mm:ss
var startTime: String = "2019-01-02 20:00:00"
//终止时间yyyy-mm-dd HH:mm:ss
var endTime: String = "2019-08-08 20:00:00"


@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun main() {
    val dir = File(filesPath)
    val files = dir.listFiles().toMutableList().also {
        println("本次共提交文件数量为${it.size}")
    }
    val list = obtainRandomTime(startTime, endTime, files.size * 2)
    var index = 0
    while (files.size > 1) {
        val userAndBranch = userAndBranches.random()
        val a = " cmd /c date ${list[index].date}"
        ExecTest.printMessage(" git config --global user.name ${userAndBranch.gitName}")
        ExecTest.printMessage(" cmd /c date ${list[index].date}")
        ExecTest.printMessage(" cmd /c time ${list[index].time}")
        /*没法通过git命令获取到一个分支是否存在*/
        if (!userAndBranch.exist) {
            ExecTest.printMessage(" git checkout -b ${userAndBranch.branchName}")
            userAndBranch.exist = true
        } else {
            ExecTest.printMessage(" git checkout ${userAndBranch.branchName}")
        }
        val randomFile = files[Random.nextInt(files.size - 1)]

        ExecTest.printMessage(" git commit ${randomFile.absolutePath} -m '${commandPrefix.random()}${randomFile.nameWithoutExtension}'")
        files.remove(randomFile)
        ExecTest.printMessage(" git checkout $mergeBranch")
        ExecTest.printMessage(" git stash")
        ExecTest.printMessage(" git merge ${userAndBranch.branchName}")
        ExecTest.printMessage(" git stash apply")
        ExecTest.printMessage(" git stash clear")
        index++
    }
}

/**
 * @param divideCount: 分割的时间数,比文件数多一半
 * */
fun obtainRandomTime(startTime: String, endTime: String, divideCount: Int): List<TimeAndDate> {
    val format = SimpleDateFormat("yyyy-mm-dd HH:mm:ss")
    val startDate = format.parse(startTime)
    val endDate = format.parse(endTime)
    val interval = (endDate.time - startDate.time) / divideCount
    val list = mutableListOf<TimeAndDate>()
    var count = 0
    var newTimeMillis = startDate.time
    var obtainTime: Date
    while (count < divideCount) {
        count++
        obtainTime = Date(newTimeMillis + (Math.random() * interval).toLong())
        //如果时间不在夜晚范围内执行添加到list中
        if (obtainTime.hours in 9..20) {
            list.add(TimeAndDate("${obtainTime.year + 1900}-${obtainTime.month + 1}-${obtainTime.date}",
                "${obtainTime.hours}:${obtainTime.minutes}:${obtainTime.seconds}"))
        }
        newTimeMillis += interval
    }
    return list
}

class ExecTest {
    companion object {
        fun printMessage(input: String) {
            val process = Runtime.getRuntime().exec(input)

//            val outputStream = process.inputStream
//            val inputStreamReader = InputStreamReader(outputStream, "gbk")
//            val bufferedReader = BufferedReader(inputStreamReader)
//            var line: String?
//            var flag = false
//            do {
//                line = bufferedReader.readLine()
//                if (flag) {
//                    println(line)
//                }
//                flag = true
//            } while (line != null)

            process.waitFor()
            println("$input 执行结果为exitValue = ${process.exitValue()}")
        }
    }
}

data class TimeAndDate(
    var date: String,
    var time: String
)