cmake_minimum_required(VERSION 3.22.1)

project("repix_native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(repix_native SHARED
    md5.cpp
    tomato_scramble.cpp
    base_pixel_scramble.cpp
    block_scramble.cpp
    row_pixel_scramble.cpp
    per_pixel_scramble.cpp
    pic_encrypt_scramble.cpp
    ScrambleEngine.cpp
    native_scramble_jni.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

target_link_libraries(repix_native
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
)
