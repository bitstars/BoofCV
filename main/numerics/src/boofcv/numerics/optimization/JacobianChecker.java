/*
 * Copyright (c) 2011-2012, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://www.boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.numerics.optimization;

import boofcv.numerics.optimization.functions.FunctionNtoM;
import boofcv.numerics.optimization.functions.FunctionNtoMxN;
import boofcv.numerics.optimization.impl.NumericalJacobianForward;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.MatrixFeatures;

/**
 * Used to validate an algebraic Jacobian numerically.
 *
 * @author Peter Abeles
 */
public class JacobianChecker {

	public static boolean jacobian( FunctionNtoM func , FunctionNtoMxN jacobian ,
									double param[] , double tol )
	{
		NumericalJacobianForward numerical = new NumericalJacobianForward(func);

		DenseMatrix64F found = new DenseMatrix64F(func.getM(),func.getN());
		DenseMatrix64F expected = new DenseMatrix64F(func.getM(),func.getN());

		jacobian.process(param,found.data);
		numerical.process(param,expected.data);

		return MatrixFeatures.isIdentical(expected,found,tol);
	}
}
