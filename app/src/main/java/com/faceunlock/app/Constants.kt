package com.faceunlock.app

object Constants {
    // Face-recognition model (downloaded into assets by CI, or placed manually).
    // Input/output dimensions are read from the model itself at runtime.
    const val MODEL_FILE = "mobile_face_net.tflite"

    // Cosine-similarity threshold for a positive match (0..1). Higher = stricter.
    // Tune per model (MobileFaceNet works well around 0.6).
    const val MATCH_THRESHOLD = 0.60f

    // Liveness (blink) thresholds from ML Kit eye-open probabilities.
    const val EYE_OPEN = 0.70f          // considered "open" above this
    const val EYE_CLOSED = 0.30f        // considered "closed" below this

    // How long a single unlock attempt session may run before giving up (ms).
    const val SESSION_TIMEOUT_MS = 12_000L

    const val NOTIF_CHANNEL = "face_unlock"
    const val NOTIF_ID = 1001
}
