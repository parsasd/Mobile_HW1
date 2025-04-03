import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Response
import com.google.gson.annotations.SerializedName
import java.util.Scanner


data class UserResponse(
    val login: String,
    val followers: Int,
    val following: Int,
    @SerializedName("created_at") val createdAt: String
)

data class RepoResponse(
    val name: String
)

data class GitHubUser(
    val username: String,
    val followers: Int,
    val following: Int,
    val createdAt: String,
    val repositories: List<String>
)

interface GitHubApi {
    @GET("users/{username}")
    suspend fun getUser(@Path("username") username: String): Response<UserResponse>

    @GET("users/{username}/repos")
    suspend fun getRepos(@Path("username") username: String): Response<List<RepoResponse>>
}

object GitHubClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: GitHubApi = retrofit.create(GitHubApi::class.java)
}

class GitHubRepository {
    private val cache = mutableMapOf<String, GitHubUser>()

    suspend fun getUser(username: String): GitHubUser? {
        if (cache.containsKey(username)) {
            println("اطلاعات کاربر از حافظه بازیابی شد.")
            return cache[username]
        }
        try {
            val userResponse = GitHubClient.api.getUser(username)
            if (!userResponse.isSuccessful) {
                println("خطا در دریافت اطلاعات کاربر: ${userResponse.code()} - ${userResponse.message()}")
                return null
            }
            val user = userResponse.body() ?: return null

            val reposResponse = GitHubClient.api.getRepos(username)
            if (!reposResponse.isSuccessful) {
                println("خطا در دریافت ریپوزیتوری‌ها: ${reposResponse.code()} - ${reposResponse.message()}")
                return null
            }
            val repos = reposResponse.body() ?: emptyList()

            val repoNames = repos.map { it.name }
            val gitHubUser = GitHubUser(
                username = user.login,
                followers = user.followers,
                following = user.following,
                createdAt = user.createdAt,
                repositories = repoNames
            )
            cache[username] = gitHubUser
            return gitHubUser
        } catch (e: Exception) {
            println("Exception occurred: ${e.message}")
            return null
        }
    }

    fun getCachedUsers(): List<GitHubUser> = cache.values.toList()

    fun searchUser(username: String): GitHubUser? = cache[username]

    fun searchByRepository(repoName: String): List<GitHubUser> =
        cache.values.filter { user ->
            user.repositories.any { it.contains(repoName, ignoreCase = true) }
        }
}

fun main() = runBlocking {
    val repository = GitHubRepository()
    val scanner = Scanner(System.`in`)
    var exit = false

    while (!exit) {
        println("\n--- منوی اطلاعات کاربران گیت‌هاب ---")
        println("1️⃣ دریافت اطلاعات کاربر بر اساس نام کاربری")
        println("2️⃣ نمایش لیست کاربران موجود در حافظه")
        println("3️⃣ جستجو بر اساس نام کاربری از بین کاربران موجود در حافظه")
        println("4️⃣ جستجو بر اساس نام ریپوزیتوری از بین داده‌های موجود در حافظه")
        println("5️⃣ خروج از برنامه")
        print("انتخاب شما: ")
        val choice = scanner.nextLine()

        when (choice) {
            "1" -> {
                print("نام کاربری را وارد کنید: ")
                val username = scanner.nextLine()
                val user = repository.getUser(username)
                if (user != null) {
                    println("\n--- اطلاعات کاربر ---")
                    println("نام کاربری: ${user.username}")
                    println("تعداد فالوورها: ${user.followers}")
                    println("تعداد فالووینگ: ${user.following}")
                    println("تاریخ ساخت اکانت: ${user.createdAt}")
                    println("ریپوزیتوری‌های پابلیک:")
                    if (user.repositories.isEmpty()) {
                        println("هیچ ریپوزیتوری‌ای یافت نشد.")
                    } else {
                        user.repositories.forEach { println("- $it") }
                    }
                } else {
                    println("کاربری با نام $username پیدا نشد یا خطایی رخ داده است.")
                }
            }
            "2" -> {
                val users = repository.getCachedUsers()
                if (users.isEmpty()) {
                    println("هیچ کاربری در حافظه موجود نیست.")
                } else {
                    println("\n--- لیست کاربران موجود در حافظه ---")
                    users.forEach { println("- ${it.username}") }
                }
            }
            "3" -> {
                print("نام کاربری برای جستجو: ")
                val searchUsername = scanner.nextLine()
                val user = repository.searchUser(searchUsername)
                if (user != null) {
                    println("\n--- کاربر یافت شده ---")
                    println("نام کاربری: ${user.username}")
                    println("تعداد فالوورها: ${user.followers}")
                    println("تعداد فالووینگ: ${user.following}")
                    println("تاریخ ساخت اکانت: ${user.createdAt}")
                    println("ریپوزیتوری‌های پابلیک:")
                    if (user.repositories.isEmpty()) {
                        println("هیچ ریپوزیتوری‌ای یافت نشد.")
                    } else {
                        user.repositories.forEach { println("- $it") }
                    }
                } else {
                    println("کاربری با نام $searchUsername در حافظه موجود نیست.")
                }
            }
            "4" -> {
                print("نام ریپوزیتوری برای جستجو: ")
                val repoName = scanner.nextLine()
                val users = repository.searchByRepository(repoName)
                if (users.isEmpty()) {
                    println("هیچ کاربری با ریپوزیتوری حاوی '$repoName' پیدا نشد.")
                } else {
                    println("\n--- کاربران دارای ریپوزیتوری شامل '$repoName' ---")
                    users.forEach { println("- ${it.username}") }
                }
            }
            "5" -> {
                println("خروج از برنامه. خداحافظ!")
                exit = true
            }
            else -> {
                println("انتخاب نامعتبر. لطفا دوباره تلاش کنید.")
            }
        }
    }
}