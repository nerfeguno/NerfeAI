cmake_minimum_required(VERSION 3.22.1)
project(nerfeai)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

add_subdirectory(
    "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp"
    EXCLUDE_FROM_ALL
)

add_library(nerfeai SHARED native-lib.cpp)

target_compile_features(nerfeai PRIVATE cxx_std_17)

target_link_libraries(
    nerfeai
    PRIVATE
    llama
    android
    log
)
