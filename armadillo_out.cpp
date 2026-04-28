#include <armadillo>
#include <iostream>
using namespace arma;
using namespace std;

int main() {
     auto a = vec({1.0, 2.0, 3.0});
     auto b = vec({4.0, 5.0, 6.0});
     auto c = a + b;
     cout << c << endl;
     auto x = vec({1.0, 2.0, 3.0});
     auto y = (x).t();
     auto z = x.n_elem;
     auto w = x.n_rows;
     cout << y << endl;
     cout << z << endl;
     cout << w << endl;
    return 0;
}