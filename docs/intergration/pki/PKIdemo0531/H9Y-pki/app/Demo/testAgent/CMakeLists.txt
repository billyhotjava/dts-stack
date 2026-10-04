cmake_minimum_required (VERSION 3.5)
project(testAgent)

# 配置include工程目录
set(PRO_INCLUDE_DIR ${PROJECT_SOURCE_DIR}/pkiAgent/include ${PROJECT_SOURCE_DIR}/parson ${PROJECT_SOURCE_DIR}/) 
include_directories(${PRO_INCLUDE_DIR})

MESSAGE(STATUS "EXECUTABLE_OUTPUT_PATH=" ${EXECUTABLE_OUTPUT_PATH})
MESSAGE(STATUS "LIBRARY_OUTPUT_PATH=" ${LIBRARY_OUTPUT_PATH})

SET(EXECUTABLE_OUTPUT_PATH ${PROJECT_BINARY_DIR}/bin) 
SET(LIBRARY_OUTPUT_PATH ${PROJECT_BINARY_DIR}/bin) 

#-D DEBUG=1
IF(DEBUG)
    add_definitions("-Wall -g")
	MESSAGE(STATUS "Compiling testAgent with '-g'")
ENDIF()

# 指定源文件
aux_source_directory(${PROJECT_SOURCE_DIR} SRC_SRC_LIST)

set(SRC_LISTS ${PROJECT_SOURCE_DIR}/async/asyncDevice.cpp
               ${PROJECT_SOURCE_DIR}/async/asyncEnroll.cpp
               ${PROJECT_SOURCE_DIR}/async/asyncSignx.cpp
               ${PROJECT_SOURCE_DIR}/sync/syncDevice.cpp
               ${PROJECT_SOURCE_DIR}/sync/syncEnroll.cpp
               ${PROJECT_SOURCE_DIR}/sync/syncSignx.cpp
               ${PROJECT_SOURCE_DIR}/sync/syncJDAuth.cpp
               ${PROJECT_SOURCE_DIR}/sync/syncUnionAuth.cpp
               ${PROJECT_SOURCE_DIR}/parson/parson.c) 

# 添加依赖库
IF(WIN32)
	MESSAGE(STATUS "Now is windows")
	
	if(CMAKE_CL_64)

		set(PRO_PKIAGENT4C_LIB ${PROJECT_SOURCE_DIR}/pkiAgent/lib/vc_x64/pkiAgent4c.lib)
	else()

		set(PRO_PKIAGENT4C_LIB ${PROJECT_SOURCE_DIR}/pkiAgent/lib/vc_x86/pkiAgent4c.lib)
	endif()
	
	# set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT")
	set(CMAKE_CXX_FLAGS_RELEASE "/MD /Od /Zi /EHsc /MP")
	set(CMAKE_EXE_LINKER_FLAGS_RELEASE "${CMAKE_EXE_LINKER_FLAGS_RELEASE} /DEBUG /OPT:REF /OPT:ICF")	
	# MESSAGE(STATUS "CMAKE_SHARED_LINKER_FLAGS_RELEASE " ${CMAKE_SHARED_LINKER_FLAGS_RELEASE})
	set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} /DEBUG /OPT:REF /OPT:ICF")
	
ELSEIF(UNIX)
	MESSAGE(STATUS "Now is UNIX-like OS's. Including aPPLE os x  and CygWin")
	MESSAGE(STATUS "PLATFORM=" ${PLATFORM})
	
	if ("${PLATFORM}" STREQUAL "")
		if(USE_32BITS)
			set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -m32 -Wl,-rpath=.")
		else()
			set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -m64 -Wl,-rpath=.")
		endif()

		set(PRO_PKIAGENT4C_LIB ${PROJECT_SOURCE_DIR}/pkiAgent/archs/amd64/libpkiAgent4c.so)
		
	elseif ("${PLATFORM}" STREQUAL "phytium")
		set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=native -Wl,-rpath=. -DARM64")
		
		set(PRO_PKIAGENT4C_LIB ${PROJECT_SOURCE_DIR}/pkiAgent/archs/arm64/libpkiAgent4c.so)
		
	elseif("${PLATFORM}" STREQUAL "loongson")
		set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=native -Wl,-rpath=.")
		

		set(PRO_PKIAGENT4C_LIB ${PROJECT_SOURCE_DIR}/pkiAgent/archs/mips64el/libpkiAgent4c.so)
		
	endif()
	
ENDIF()

set(PRO_LIB_ALL ${PRO_PKIAGENT4C_LIB})
add_executable(testAgent ${SRC_SRC_LIST} ${SRC_LISTS})
target_link_libraries(testAgent ${PRO_LIB_ALL})
