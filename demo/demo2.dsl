// Demo 2: More complex VecDSL program with loop and branching

a = [1, 2, 3];
b = [4, 5, 6];
c = [0, 0, 0];
i = 0;

while (i < 3) {
    c = c + b;
    i = i + 1;
}

if (a[0] > 0) {
    d = c;
} else {
    d = a;
}

print(c);
print(d);