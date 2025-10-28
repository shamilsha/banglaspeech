package com.example.bengalipractice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.bengalipractice.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), RecognitionListener {

    private val apiKey = "AIzaSyBEQukhkVCX_4xed29ByJYxszIDn8IPNgM"
    private val apiUrl = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"

    private val httpClient = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false
    private var answerFound = false
    private var latestPartialResults: ArrayList<String>? = null

    private val quizPhrases = listOf(
        QuizItem("I am going home now.", "আমি এখন বাড়ি যাচ্ছি।"),
        QuizItem("What is your name?", "আপনার নাম কি?"),
        QuizItem("Please wait a little.", "দয়া করে একটু অপেক্ষা করুন।"),
        QuizItem("How are you?", "আপনি কেমন আছেন?"),
        QuizItem("It is a beautiful day.", "এটি একটি সুন্দর দিন।"),
        QuizItem("I am hungry.", "আমার খিদে পেয়েছে।"),
        QuizItem("Where is the market?", "বাজার কোথায়?"),
        QuizItem("The weather is very hot.", "আবহাওয়া খুব গরম।"),
        QuizItem("I need help.", "আমার সাহায্য দরকার।"),
        QuizItem("Can you speak English?", "আপনি কি ইংরেজি বলতে পারেন?"),
        QuizItem("January", "জানুয়ারি।"),
        QuizItem("February", "ফেব্রুয়ারী।"),
        QuizItem("March", "মার্চ।"),
        QuizItem("April", "এপ্রিল।"),
        QuizItem("May", "মে।"),
        QuizItem("June", "জুন।"),
        QuizItem("July", "জুলাই।"),
        QuizItem("August", "আগস্ট।"),
        QuizItem("September", "সেপ্টেম্বর।"),
        QuizItem("October", "অক্টোবর।"),
        QuizItem("November", "নভেম্বর।"),
        QuizItem("December", "ডিসেম্বর।"),
        QuizItem("Sunday", "রবিবার।"),
        QuizItem("Monday", "সোমবার।"),
        QuizItem("Tuesday", "মঙ্গলবার।"),
        QuizItem("Wednesday", "বুধবার।"),
        QuizItem("Thursday", "বৃহস্পতিবার।"),
        QuizItem("Friday", "শুক্রবার।"),
        QuizItem("Saturday", "শনিবার।"),
        QuizItem("Zero", "শূন্য।"),
        QuizItem("One", "এক।"),
        QuizItem("Two", "দুই।"),
        QuizItem("Three", "তিন।"),
        QuizItem("Four", "চার।"),
        QuizItem("Five", "পাঁচ।"),
        QuizItem("Six", "ছয়।"),
        QuizItem("Seven", "সাত।"),
        QuizItem("Eight", "আট।"),
        QuizItem("Nine", "নয়।"),
        QuizItem("Ten", "দশ।"),
        QuizItem("Eleven", "এগারো।"),
        QuizItem("Twelve", "বারো।"),
        QuizItem("Thirteen", "তেরো।"),
        QuizItem("Fourteen", "চোদ্দো।"),
        QuizItem("Fifteen", "পনেরো।"),
        QuizItem("Sixteen", "ষোলো।"),
        QuizItem("Seventeen", "সতেরো।"),
        QuizItem("Eighteen", "আঠারো।"),
        QuizItem("Nineteen", "উনিশ।"),
        QuizItem("Twenty", "বিশ।"),
        QuizItem("Twenty-one", "একুশ।")
    )

    private var currentQuestionIndex = 0

    private val numberWordMap = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
        "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
        "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "twenty-one" to "21"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        binding.repeatButton.setOnClickListener { repeatPrompt() }
        binding.skipButton.setOnClickListener { nextQuestion() }
        binding.hintButton.setOnClickListener { showHint() }
        binding.nextButton.setOnClickListener { nextQuestion() }

        initQuiz()
    }

    private fun initQuiz() {
        currentQuestionIndex = -1
        nextQuestion()
    }

    private fun nextQuestion() {
        answerFound = false
        latestPartialResults = null
        currentQuestionIndex = (currentQuestionIndex + 1) % quizPhrases.size
        val data = quizPhrases[currentQuestionIndex]

        binding.bengaliPromptTextView.text = data.bengali
        binding.nextButton.isEnabled = false
        binding.feedbackTextView.text = ""
        binding.correctAnswerTextView.text = ""
        binding.partialResultsTextView.text = ""

        generateAndPlayAudio(data.bengali, "bn-BD", thenListen = true)
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // Use Indian English
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
        isListening = true
    }

    private fun stopListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.ROOT).trim().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")
    }

    private fun isAnswerCorrect(spokenTexts: ArrayList<String>): Boolean {
        val data = quizPhrases[currentQuestionIndex]
        val normalizedCorrectWord = normalizeText(data.english)

        val acceptableAnswers = mutableListOf(normalizedCorrectWord)
        numberWordMap[normalizedCorrectWord]?.let { acceptableAnswers.add(it) }

        for (spokenText in spokenTexts) {
            val normalizedSpoken = normalizeText(spokenText)
            if (acceptableAnswers.contains(normalizedSpoken)) {
                return true
            }
        }
        return false
    }

    private fun processFinalAnswer(spokenTexts: ArrayList<String>) {
        if (answerFound) return // Already processed a correct answer from partial results
        answerFound = true
        stopListening()

        val isCorrect = isAnswerCorrect(spokenTexts)
        val allDetected = spokenTexts.joinToString(", ")

        binding.nextButton.isEnabled = true
        binding.correctAnswerTextView.text = getString(R.string.correct_answer_display, quizPhrases[currentQuestionIndex].english)

        if (isCorrect) {
            val feedbackText = getString(R.string.audio_feedback_correct)
            binding.feedbackTextView.text = getString(R.string.correct_answer_feedback)
            generateAndPlayAudio(feedbackText, "en-US", thenListen = false) { // Pass a lambda for onCompletion
                binding.nextButton.postDelayed({ nextQuestion() }, 1000)
            }
        } else {
            val incorrectFeedbackIntro = getString(R.string.audio_feedback_incorrect)
            val feedbackToSpeak = "$incorrectFeedbackIntro ${quizPhrases[currentQuestionIndex].english}"
            val allAcceptable = let {
                val data = quizPhrases[currentQuestionIndex]
                val normalizedCorrectWord = normalizeText(data.english)
                val answers = mutableListOf(normalizedCorrectWord)
                numberWordMap[normalizedCorrectWord]?.let { answers.add(it) }
                answers.joinToString(" or ")
            }
            binding.feedbackTextView.text = getString(R.string.incorrect_answer_details, allDetected, allAcceptable)
            generateAndPlayAudio(feedbackToSpeak, "en-US", thenListen = false)
        }
    }

    private fun repeatPrompt() {
        answerFound = false
        latestPartialResults = null
        binding.feedbackTextView.text = ""
        binding.correctAnswerTextView.text = ""
        binding.partialResultsTextView.text = ""
        binding.nextButton.isEnabled = false

        val data = quizPhrases[currentQuestionIndex]
        generateAndPlayAudio(data.bengali, "bn-BD", thenListen = true)
    }

    private fun showHint() {
        val data = quizPhrases[currentQuestionIndex]
        generateAndPlayAudio(data.english, "en-US", thenListen = false)
    }

    private fun generateAndPlayAudio(text: String, languageCode: String, thenListen: Boolean, onCompletion: (() -> Unit)? = null) {
        lifecycleScope.launch {
            try {
                binding.repeatButton.isEnabled = false

                val jsonPayload = JSONObject().apply {
                    put("input", JSONObject().apply { put("text", text) })
                    put("voice", JSONObject().apply { put("languageCode", languageCode) })
                    put("audioConfig", JSONObject().apply {
                        put("audioEncoding", "LINEAR16")
                        put("sampleRateHertz", SAMPLE_RATE)
                    })
                }

                val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(apiUrl).post(requestBody).build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }

                if (!response.isSuccessful) {
                    throw Exception("API call failed with code ${response.code}: ${response.body?.string()}")
                }

                val responseBody = response.body!!.string()
                val responseJson = JSONObject(responseBody)
                val audioContent = responseJson.optString("audioContent")

                if (audioContent.isNullOrEmpty()) {
                    throw Exception("No audio data received from API.")
                }

                val pcmData = Base64.decode(audioContent, Base64.DEFAULT)
                val tempFile = File(cacheDir, "temp_tts_audio.wav")
                writePcmToWav(tempFile, pcmData)

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                        binding.repeatButton.isEnabled = true
                        if (thenListen) {
                            startListening()
                        } else {
                            onCompletion?.invoke()
                        }
                    }
                    prepare()
                    start()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.feedbackTextView.text = getString(R.string.audio_error, e.message)
                }
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    if (mediaPlayer == null || !mediaPlayer!!.isPlaying) {
                        binding.repeatButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun writePcmToWav(file: File, pcmData: ByteArray) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = pcmData.size

        FileOutputStream(file).use { output ->
            output.write("RIFF".toByteArray())
            output.write(intToByteArray(36 + dataSize))
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.write(intToByteArray(16))
            output.write(shortToByteArray(1))
            output.write(shortToByteArray(numChannels.toShort()))
            output.write(intToByteArray(SAMPLE_RATE))
            output.write(intToByteArray(byteRate))
            output.write(shortToByteArray(blockAlign.toShort()))
            output.write(shortToByteArray(bitsPerSample.toShort()))
            output.write("data".toByteArray())
            output.write(intToByteArray(dataSize))
            output.write(pcmData)
        }
    }

    private fun intToByteArray(i: Int): ByteArray {
        return byteArrayOf(
            (i and 0xFF).toByte(),
            (i shr 8 and 0xFF).toByte(),
            (i shr 16 and 0xFF).toByte(),
            (i shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(s: Short): ByteArray {
        return byteArrayOf((s.toInt() and 0xFF).toByte(), (s.toInt() shr 8 and 0xFF).toByte())
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }

    // RecognitionListener methods
    override fun onReadyForSpeech(params: Bundle?) {
        binding.partialResultsTextView.text = getString(R.string.listening)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            latestPartialResults = matches
            binding.partialResultsTextView.text = matches[0]
            if (isAnswerCorrect(matches)) {
                processFinalAnswer(matches)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!answerFound) {
            if (!matches.isNullOrEmpty()) {
                processFinalAnswer(matches)
            } else if (latestPartialResults != null) {
                processFinalAnswer(latestPartialResults!!)
            }
        }
    }

    override fun onError(error: Int) {
        if (answerFound) return
        isListening = false
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> getString(R.string.error_audio)
            SpeechRecognizer.ERROR_CLIENT -> getString(R.string.error_client)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.error_insufficient_permissions)
            SpeechRecognizer.ERROR_NETWORK -> getString(R.string.error_network)
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.error_network_timeout)
            SpeechRecognizer.ERROR_NO_MATCH -> {
                if (latestPartialResults != null) {
                    processFinalAnswer(latestPartialResults!!)
                    return
                }
                getString(R.string.error_no_match)
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.error_recognizer_busy)
            SpeechRecognizer.ERROR_SERVER -> getString(R.string.error_server)
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.error_speech_timeout)
            else -> getString(R.string.speech_recognition_failed)
        }
        binding.feedbackTextView.text = errorMessage
        binding.nextButton.isEnabled = true
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}