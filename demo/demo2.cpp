#include <armadillo>
#include <iostream>
using namespace arma;
using namespace std;

int main() {
     auto a = vec({1, 2, 3});
     auto b = vec({4, 5, 6});
     auto c = vec({0, 0, 0});
     auto d = vec({0, 0, 1});
     auto c = b + d;
     auto i = 0;
     while ((i < 3)) {
         auto c = c + b;
         auto i = i + 1;
    }
     if (((a.at(0)) > 0)) {
         auto d = c;
    } else {
         auto d = a;
    }
    return 0;
}