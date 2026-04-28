# Compiler
CXX := clang++

# Target
TARGET := main

# Source files
SRCS := armadillo_out.cpp

# Default flags
CXXFLAGS := -std=c++17 -O2 -Wall -Wextra

# Homebrew paths (Apple Silicon default)
INCLUDES := -I/opt/homebrew/include
LDFLAGS  := -L/opt/homebrew/lib

# Libraries
LDLIBS := -larmadillo

# Build rule
$(TARGET): $(SRCS)
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET) $(INCLUDES) $(LDFLAGS) $(LDLIBS)

# Clean rule
clean:
	rm -f $(TARGET)
