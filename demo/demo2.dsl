a = [1, 2, 3]
b = [4, 5, 6]
offset = [0, 0, 1]
sum = b + offset
loopSeed = a->0
loopLimit = 3
first = a->0

while (loopSeed < loopLimit) {
    loopValue = sum + b
    print(loopValue)
}

if (first > 0) {
    print(sum)
} else {
    print(a)
}
