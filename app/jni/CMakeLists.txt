cmake_minimum_required(VERSION 3.22 FATAL_ERROR)

project(Kahon LANGUAGES C CXX)

set(KAD_STATIC ON)
set(KAD_LIBRARY ON)
set(KAD_LTO ON)
set(KAD_CAPTURE OFF)

add_subdirectory(
	"${CMAKE_CURRENT_SOURCE_DIR}/../../submodules/kad"
	"${CMAKE_CURRENT_BINARY_DIR}/kad"
)
