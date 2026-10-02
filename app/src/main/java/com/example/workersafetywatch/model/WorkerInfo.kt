package com.example.workersafetywatch.model

/**
 * 작업자 정보 데이터 모델.
 *
 * 스마트폰 앱에서 Data Layer를 통해 전달받은 작업자 정보를
 * 워치에 저장하고 화면에 표시할 때 사용한다.
 *
 * @property workerId 작업자 고유 ID (예: "W-001")
 * @property workerName 작업자 이름
 * @property department 소속 부서 (선택, null 가능)
 */
data class WorkerInfo(
    val workerId: String,
    val workerName: String,
    val department: String?
) {
    companion object {
        /** 작업자 정보가 아직 등록되지 않았을 때 사용하는 기본값 */
        val DEFAULT = WorkerInfo(
            workerId = "미등록",
            workerName = "미등록 작업자",
            department = null
        )
    }
}
