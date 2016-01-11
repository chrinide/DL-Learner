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
package org.dllearner.core.owl;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.dllearner.core.AbstractReasonerComponent;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a class subsumption hierarchy (ignoring equivalent concepts).
 * 
 * @author Jens Lehmann
 * 
 */
public class LazyClassHierarchy extends ClassHierarchy {

	public static Logger logger = LoggerFactory.getLogger(LazyClassHierarchy.class);
	
	private AbstractReasonerComponent rc;
	
	public LazyClassHierarchy(AbstractReasonerComponent rc) {
		super(new TreeMap<OWLClassExpression, SortedSet<OWLClassExpression>>(), new TreeMap<OWLClassExpression, SortedSet<OWLClassExpression>>());
		this.rc = rc;
	}

	@Override
	public SortedSet<OWLClassExpression> getSuperClasses(OWLClassExpression concept, boolean direct) {
		return rc.getSuperClasses(concept);
	}

	@Override
	public SortedSet<OWLClassExpression> getSubClasses(OWLClassExpression concept, boolean direct) {
		return rc.getSubClasses(concept);
	}

	@Override
	public LazyClassHierarchy clone() {
		return new LazyClassHierarchy(rc);		
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.owl.AbstractHierarchy#cloneAndRestrict(java.util.Set)
	 */
	@Override
	public AbstractHierarchy<OWLClassExpression> cloneAndRestrict(Set<? extends OWLClassExpression> allowedEntities) {
		return new LazyClassHierarchy(rc);
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.owl.AbstractHierarchy#thinOutSubsumptionHierarchy()
	 */
	@Override
	public void thinOutSubsumptionHierarchy() {
		// do nothing here because we don't have anything precomputed
	}
}
