cmake_minimum_required(VERSION 3.22.1)
project("legendaoffline")

include(FetchContent)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG master
    GIT_SHALLOW TRUE
)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(whisper_jni SHARED native-lib.cpp)
target_include_directories(whisper_jni PRIVATE ${whisper_SOURCE_DIR}/include)
target_link_libraries(whisper_jni PRIVATE whisper android log)
target_compile_features(whisper_jni PRIVATE cxx_std_17)
