package spider65.ebike.tsdz2_esp32.utils;

public class Cramer {

    double [][]A;
    double []B;
    int size;

    public Cramer() {
        A = null;
        B = null;
        size = 0;
    }

    public Cramer (double[][] m2, double[] x) {
        this.A = m2;
        this.B = x;
        this.size = x.length;
    }

    public double[] solve () {
        double [][]tempMatrix = new double[size][size];
        double []x = new double[size];
        double detCohef = this.calculateCoeficientsMatrixDeterminant();
        for (int i=0;i<size;i++) {
            for (int k=0;k<size;k++) {
                for (int l=0;l<size;l++) {
                    tempMatrix[k][l] = A[k][l];
                }
            }
            for (int k=0;k<size;k++) {
                tempMatrix[k][i] = B[k];
            }
            double det = this.calculateDeterminant(tempMatrix, size);
            x[i] = det/detCohef;
        }
        return x;
    }

    public double calculateCoeficientsMatrixDeterminant() {
        return calculateDeterminant(this.A, this.size);
    }

    public double calculateDeterminant (double [][]m, int sizeM ) {
        if ( sizeM == 1 ) {
            return m[0][0];
        } else if ( sizeM == 2 ) {
            // You should save a lot of recursive calls by calculating
            // a 2x2 determinant here
            return m[0][0] * m[1][1] - m[0][1] * m[1][0];
        } else {
            double sum = 0.0;
            int multiplier = -1;
            for (int i=0; i<sizeM;i++) {
                multiplier = multiplier == -1 ? 1 : -1;
                double [][]nM = this.copyMatrix(m, sizeM, i);
                double det = multiplier * m[0][i] * calculateDeterminant(nM, sizeM-1);
                sum += det;
            }
            return sum;
        }
    }

    public double[][] copyMatrix (double [][]m, int size, int col) {
        int sizeM = size -1;
        double [][]result = new double[sizeM][sizeM];
        int nI = 0;
        for (int i=1; i<size;i++) {
            int nJ = 0;
            for (int j=0; j<size;j++) {
                if ( j != col) {
                    result[nI][nJ] = m[i][j];
                    nJ++;
                }
            }
            nI++;
        }
        return result;
    }
}
