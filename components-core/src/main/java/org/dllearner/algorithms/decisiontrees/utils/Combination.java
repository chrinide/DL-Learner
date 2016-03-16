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

package org.dllearner.algorithms.decisiontrees.utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Methoden zur Erzeugung von Kombinationen.
 */

public class Combination {

    /* ______________________________________________________________________________________________________________ *\
    \* Konstanten                                                                                                     */

    /* ______________________________________________________________________________________________________________ *\
    \* Klassenvariablen                                                                                               */

    /* ______________________________________________________________________________________________________________ *\
    \* Instanzvariablen                                                                                               */

    /* ______________________________________________________________________________________________________________ *\
    \* Konstruktoren                                                                                                  */

    /**
     * Erzeugt eine Instanz dieser Klasse.
     *
     * Da verhindert werden soll, dass Instanzen dieser Klasse ausserhalb dieser Klasse erzeugt werdem, ist dieser
     * Konstruktor 'private' deklariert.
     */

    private Combination()
    {
        super();
    }

    /* ______________________________________________________________________________________________________________ *\
    \* Instanzmethoden                                                                                                */

    /* ______________________________________________________________________________________________________________ *\
    \* Klassenmethoden                                                                                                */
    
    
    /**
     * Liefert eine Liste aller Kombinationen (ohne Duplikate) der Elemente der angegebenen Liste. 
     * 
     * Beispiel: Angenommen, die angegebene Liste ist "[a, b, c]", dann wre das Ergebnis eine Liste aus Listen wie
     * folgend: "[[], [a], [a, b], [a, c],[b], [b, c], [c]]".
     *
     * @param <T>               Der Typ der ELemente
     * @param elements          Die Elemente
     * @return                  Alle Kombinationen der Elemente 
     */
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T extends Comparable<? super T>> List<List<T>> findCombinations(Collection elements)
    {
        List<List<T>> result = new ArrayList<>();
        
        for (int i = 0; i <= elements.size(); i++)
            result.addAll(findCombinations(elements, i));

        return result;
    }
    
    
    /**
     * Liefert eine Liste aller Kombinationen (ohne Duplikate) der Elemente der angegebenen Liste, die eine bestimmte
     * Anzahl umfasst. 
     * 
     * Beispiel 1: Angenommen, die angegebene Liste ist "[a, b, c, d]" und die Anzahl ist 2, dann wre das Ergebnis eine
     * Liste aus Listen wie folgend: "[[a, b], [a, c], [a, d], [b, c], [b, d], [], [c, d]]".
     * 
     * Beispiel 2: Angenommen, die angegebene Liste ist "[a, b, c]" und die Anzahl ist 3, dann wre das Ergebnis eine
     *
     * @param <T>               Der Typ der ELemente
     * @param elements          Die Elemente
     * @param n                 Die Anzahl der Elemente
     * @return                  Alle Kombinationen der Elemente 
     */
    
    public static <T extends Comparable<? super T>> List<List<T>> findCombinations(Collection<T> elements, int n)
    {
        List<List<T>> result = new ArrayList<>();
        
        if (n == 0)
        {
            result.add(new ArrayList<>());
            
            return result;
        }
        
        List<List<T>> combinations = findCombinations(elements, n - 1);
        for (List<T> combination: combinations)
        {
            for (T element: elements)
            {
                if (combination.contains(element))
                {
                    continue;
                }
                
                List<T> list = new ArrayList<>();

                list.addAll(combination);
                
                if (list.contains(element))
                    continue;
                
                list.add(element);
                Collections.sort(list);
                
                if (result.contains(list))
                    continue;
                
                result.add(list);
            }
        }
        
        return result;
    }

    /* ______________________________________________________________________________________________________________ *\
    \* Klassen                                                                                                        */

}

/*\
 * _____________________________________________________________________________________________________________________
 * 
 * This software is distributed under the terms of the BSD License:
 * 
 * Copyright  2006 Klaus Rogall, Hamburg, Germany (klaus.rogall@web.de). All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *   disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of the Klaus Rogall nor the names of its contributors may be used to endorse or promote products
 *   derived from this software without specific prior written permission.
 *   
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * _____________________________________________________________________________________________________________________
\*/
