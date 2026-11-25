cmake_minimum_required(VERSION 3.22.1)
project("lyra_voice_dsp" LANGUAGES CXX)

add_library(
    lyra_voice_dsp
    SHARED
    native-lib.cpp
)

target_compile_features(lyra_voice_dsp PRIVATE cxx_std_17)

target_compile_options(
    lyra_voice_dsp
    PRIVATE
    -O3
    -ffast-math
    -Wall
    -Wextra
)

find_library(log-lib log)

target_link_libraries(
    lyra_voice_dsp
    ${log-lib}
)
