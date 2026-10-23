cmake_minimum_required(VERSION 3.22.1)

project("ebsynth_native" LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -Wall -Wextra -Wno-unused-parameter -pthread")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+simd")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
endif()

add_library(
    ebsynth_native
    SHARED
    ebsynth_core.cpp
    ebsynth_jni.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(egl-lib EGL)
find_library(gles3-lib GLESv3)

target_link_libraries(
    ebsynth_native
    ${log-lib}
    ${jnigraphics-lib}
    ${egl-lib}
    ${gles3-lib}
)
