package org.hyperskill.blackboard.ui.teacher.details

import android.os.Handler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import okhttp3.Call
import okhttp3.Response
import org.hyperskill.blackboard.data.model.Credential
import org.hyperskill.blackboard.data.model.Student
import org.hyperskill.blackboard.network.student.dto.GradesResponse
import org.hyperskill.blackboard.network.teacher.TeacherClient
import org.hyperskill.blackboard.util.Util
import java.io.IOException

class TeacherStudentDetailsViewModel(
        private val teacherClient: TeacherClient,
        private val handler: Handler,
        val onUpdateSuccess: () -> Unit
) : ViewModel() {

    private val _grades = MutableStateFlow(listOf<Int>())
    val grades: StateFlow<List<Int>> get() = _grades

    private var _examGrade = MutableStateFlow(-1)
    val examGrade: StateFlow<Int> get() = _examGrade

    private val _editedGrades = MutableStateFlow(listOf<Int>())
    val editedGrades : StateFlow<List<Int>> get() = _editedGrades
            .also { println("_editedGrades ${it.value}") }

    private var _editedExamGrade = MutableStateFlow(-1)
    val editedExamGrade: StateFlow<Int> get() = _editedExamGrade
            .also { println("_editedExamGrade ${it.value}") }

    val partialGrade = editedGrades.map { grades ->
        println("partialGrade = editedGrades.map")
        (if(grades.isEmpty())
            0
        else
            grades.sumOf { if (it < 0) 0 else it } / grades.size)
        .also { println("partialGrade $it") }
    }

    val isExamEnabled = partialGrade.map { partialGrade ->
        println("isExamEnabled = partialGrade.map")
        (partialGrade in (30 until 70)).also { println("isExamEnabled $it") }
    }

    val finalGrade = partialGrade.combine(isExamEnabled) { partial, isExamEnabled ->
        partial to isExamEnabled
    }.combine(editedExamGrade) { (partial, isExamEnabled), exam ->
        when {
            exam < 0 -> partial
            isExamEnabled -> (partial + exam) / 2
            else -> partial
        }.also { println("finalGrade $it") }
    }

    val partialResult = partialGrade.map {partialGrade ->
        "Partial Result: $partialGrade".also { println("partialResult $it") }
    }

    val finalResult = finalGrade.map { finalGrade ->
        "Final Result: $finalGrade"
    }

    private val _networkErrorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val networkErrorMessage: StateFlow<String?>
        get() = _networkErrorMessage



    fun updateGrades(credential: Credential, studentName: String) {
        _grades.value = _editedGrades.value
        _examGrade.value = editedExamGrade.value

        teacherClient.updateGradesRequest(
            credential, studentName, editedGrades.value, editedExamGrade.value , Util.callback(
                onFailure = ::onCallFailure,
                onResponse = ::onUpdateGradesResponse
            )
        )
    }

    private var fetchGradesCall : Call? = null

    fun fetchGrades(credential: Credential, student: Student) {
        fetchGradesCall = teacherClient.fetchStudentGrades(
            credential, student, Util.callback(
                onFailure = ::onCallFailure,
                onResponse = ::onFetchGradesResponse
            )
        )
    }

    private fun onCallFailure(call: Call, e: IOException) {
        println(call)
        handler.post {
            _networkErrorMessage.value = e.message
        }
    }

    private fun onUpdateGradesResponse(call: Call, response: Response) {
        println(call)
        handler.apply {
            println(response)
            when(response.code) {
                200 -> {
                    post { onUpdateSuccess() }
                }
                else -> {
                    post {
                        _networkErrorMessage.value = "${response.code} ${response.message}"
                    }
                }
            }
        }
    }

    private fun onFetchGradesResponse(call: Call, response: Response) {
        println(call)
        handler.apply {
            println(response)
            when(response.code) {
                200 -> {
                    val gradesResponse = teacherClient.parseGrades(response.body)
                    if(gradesResponse == null || gradesResponse is GradesResponse.Fail) {
                        post {
                            _networkErrorMessage.value =
                                "server error, invalid response body ${response.body?.string()}"
                        }
                    } else {
                        post {
                            (gradesResponse as? GradesResponse.Success)?.also {

                                _grades.value = it.grades
                                _editedGrades.value = it.grades
                                _examGrade.value = it.exam
                                _editedExamGrade.value = it.exam
                            }
                        }
                    }
                }
                else -> {
                    post {
                        _networkErrorMessage.value = "${response.code} ${response.message}"
                    }
                }
            }
        }
    }

    fun setEditedGrades(editedGrades: List<Int>) {
        println("setEditedGrades $editedGrades, isEqualToLast ${editedGrades == _editedGrades.value}")
        _editedGrades.value = editedGrades
    }

    fun setEditedExamGrades(editedExamGrades: Int) {
        _editedExamGrade.value = editedExamGrades
    }

    class Factory(private val teacherClient: TeacherClient, private val handler: Handler, val onUpdateSuccess: () -> Unit) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TeacherStudentDetailsViewModel(teacherClient, handler, onUpdateSuccess) as T
        }
    }
}