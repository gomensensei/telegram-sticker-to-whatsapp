cmake_minimum_required(VERSION 3.22.1)

project(tgwa_maker LANGUAGES C CXX)

include(FetchContent)

set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_LIBWEBPMUX ON CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    libwebp
    URL
        https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0.tar.gz
    URL_HASH
        SHA256=93a852c2b3efafee3723efd4636de855b46f9fe1efddd607e1f42f60fc8f2136
)
FetchContent_MakeAvailable(libwebp)

add_library(
    tgwa_maker
    SHARED
    native_webp_encoder.cpp
)

find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)

target_link_libraries(
    tgwa_maker
    PRIVATE
    libwebpmux
    webp
    ${android-lib}
    ${jnigraphics-lib}
    ${log-lib}
)
