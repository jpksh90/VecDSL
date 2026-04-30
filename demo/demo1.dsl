-- Vector addition demo
let a = [1.4, 2.8, 3.9]
let b = [4.2, 5.4, 6]
let c = a + b
print(c)

-- Demonstrate transpose, length, and dimension operators
let x = [1, 2, 3]
let y = x->tpos + (b->tpos + c->tpos)
let z = x->len
let w = x->dim
print(y)
print(z)
print(w)
