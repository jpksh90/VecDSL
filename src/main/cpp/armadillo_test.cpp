#include <armadillo>
#include <iostream>

using namespace arma;
using namespace std;

int main() {
    vec a = {1, 2, 3};
    vec b = {4, 5, 6};
    vec c = a + b;
    cout << "a = " << a.t();
    cout << "b = " << b.t();
    cout << "c = a + b = " << c.t();
    return 0;
}

