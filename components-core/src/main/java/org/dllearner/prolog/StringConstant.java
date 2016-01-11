/**
 * Copyright (C) 2007 - 2016, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dllearner.prolog;

/**
 * 
 * @author Sebastian Bader
 * 
 */
public class StringConstant extends Constant {
	private String string;

	public StringConstant(String src) {
		string = src;
	}

	public String getString() {
		return string;
	}

	@Override
	public boolean isGround() {
		return true;
	}

	@Override
	public String toString() {
		return "C[" + string + "]";
	}

	@Override
	public String toPLString() {
		return string;
	}

	@Override
	public Term getInstance(Variable variable, Term term) {
		return new StringConstant(string);
	}

	@Override
	public boolean equals(Object obj) {
		return string.equals(obj);
	}

	@Override
	public int hashCode() {
		return string.hashCode();
	}

	@Override
	public Object clone() {
		return new StringConstant(string);
	}
}