let a = [1, 2, 3]
let b = [4, 5, 6]
let offset = [0, 0, 1]
let sum = b + offset
let loopSeed = a->0
let loopLimit = 3
let first = a->0
let loopValue = [0,0,0]
while (loopSeed < loopLimit) {
    loopValue = sum + b
    print(loopValue)
}

if (first > 0) {
    print(sum)
} else {
    print(a)
}
