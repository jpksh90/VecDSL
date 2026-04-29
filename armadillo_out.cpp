#include <armadillo>
#include <iostream>
using namespace arma;
using namespace std;

int main() {
     auto a = vec({1.0, 2.0, 3.0});
     auto b = vec({4.0, 5.0, 6.0});
     auto c = vec({0.0, 0.0, 0.0});
     auto d = vec({0.0, 0.0, 1.0});
     auto c = b + d;
     auto i = 0.0;
     while ((i < 3.0)) {
         auto c = c + b;
         auto i = i + 1.0;
    }
     if (((a.at(0.0)) > 0.0)) {
         auto d = c;
    } else {
         auto d = a;
    }
     cout << c << endl;
     cout << d << endl;
    return 0;
}