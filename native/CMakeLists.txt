cmake_minimum_required(VERSION 3.22.1)
project("whisper-android")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ARM v8 specific optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp16")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a+fp16")
    add_definitions(-DGGML_USE_FP16_VA=1)
    add_definitions(-DARM_NEON=1)
endif()

# Add whisper.cpp source files
add_subdirectory(${CMAKE_SOURCE_DIR}/whisper.cpp)

# Create shared library
add_library(whisper-jni SHARED
    jni/whisper_jni.cpp
    jni/audio_processor.cpp
)

# Link libraries
target_link_libraries(whisper-jni
    whisper
    log
    android
)