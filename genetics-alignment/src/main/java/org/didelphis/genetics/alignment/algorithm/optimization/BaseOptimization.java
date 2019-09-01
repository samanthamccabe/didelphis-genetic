/******************************************************************************
 * General components for language modeling and analysis                      *
 *                                                                            *
 * Copyright (C) 2014-2019 Samantha F McCabe                                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 *                                                                            *
 ******************************************************************************/

package org.didelphis.genetics.alignment.algorithm.optimization;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiPredicate;
/**
 * Class {@code Optimization}
 *
 * @author Samantha Fiona McCabe
 * @since 0.1.0 Date: 2017-07-05
 */
@ToString
@EqualsAndHashCode
public final class BaseOptimization implements Optimization {

	public static final Optimization MAX = new BaseOptimization(
			(x, y) -> x >= y, Double.MIN_VALUE
	);

	public static final Optimization MIN = new BaseOptimization(
			(x, y) -> x <= y, Double.MAX_VALUE
	);

	private final @NonNull BiPredicate<? super Double, ? super Double> predicate;
	private final double defaultValue;

	/**
	 * Creates a basic {@link Optimization} using the provided values
	 * @param predicate a {@link BiPredicate}; tests if the first parameter is
	 * more optimal than the second, the meaning of "optimal" being dependant on
	 * the optimization's implementation
	 * @param defaultValue a default value to use
	 */
	public BaseOptimization(
			@NonNull BiPredicate<? super Double, ? super Double> predicate,
			double defaultValue
	) {
		this.predicate = predicate;
		this.defaultValue = defaultValue;
	}

	@Override
	public boolean test(Double x, Double y) {
		return predicate.test(x, y);
	}

	@Override
	public double defaultValue() {
		return defaultValue;
	}

}