package org.drools.core.common;

import org.drools.core.util.FastIterator;
import org.drools.core.util.Iterator;
import org.drools.core.util.ObjectHashSet;
import org.drools.core.util.ObjectHashSet.ObjectEntry;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.ObjectTypeNode.ObjectTypeNodeMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.RightTuple;

public class LeftTupleIterator
    implements
    Iterator {
    private InternalWorkingMemory wm;

    private LeftTupleSink         node;

    private LeftTuple             currentLeftTuple;

    // Iterates object type nodes
    private ObjectEntry           factHandleEntry;
    Iterator                      otnIterator;

    LeftTupleIterator() {

    }

    private LeftTupleIterator(InternalWorkingMemory wm,
                              LeftTupleSink         node) {
        this.wm = wm;
        this.node = node;        
        setFirstLeftTupleForNode();
    }
    
    public static LeftTupleIterator iterator(InternalWorkingMemory wm,
                                             LeftTupleSink         node) {
        return new LeftTupleIterator(wm, node);
    }

    public void setFirstLeftTupleForNode() {
        LeftTupleSource source = node.getLeftTupleSource();

        this.currentLeftTuple = getFirstLeftTuple( source,
                                                   (LeftTupleSink) node,
                                                   wm );
    }

    public LeftTuple getFirstLeftTuple(LeftTupleSource source,
                                       LeftTupleSink sink,
                                       InternalWorkingMemory wm) {
        if ( source instanceof JoinNode || source instanceof NotNode || source instanceof FromNode ||source instanceof AccumulateNode ) {

            BetaMemory memory;
            FastIterator localIt;
            if ( source instanceof FromNode ) {
                memory = ((FromMemory) wm.getNodeMemory( (MemoryFactory) source )).betaMemory;
            } else if ( source instanceof AccumulateNode ) {
                memory = ((AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source )).betaMemory;
            } else {
                memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
            }

            localIt = memory.getLeftTupleMemory().fullFastIterator();
            LeftTuple leftTuple = BetaNode.getFirstLeftTuple( memory.getLeftTupleMemory(),
                                                              localIt );

            while ( leftTuple != null ) {
                for ( LeftTuple childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getLeftParentNext() ) {
                    if ( childleftTuple.getLeftTupleSink() == sink ) {
                        return childleftTuple;
                    }
                }
                leftTuple = (LeftTuple) localIt.next( leftTuple );
            }
        }
        if ( source instanceof ExistsNode ) {
            BetaMemory memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
            FastIterator localIt = memory.getRightTupleMemory().fullFastIterator();

            RightTuple rightTuple = BetaNode.getFirstRightTuple( memory.getRightTupleMemory(),
                                                                 localIt );

            while ( rightTuple != null ) {
                if ( rightTuple.getBlocked() != null ) {
                    for ( LeftTuple leftTuple = rightTuple.getBlocked(); leftTuple != null; leftTuple = leftTuple.getBlockedNext() ) {
                        for ( LeftTuple childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getLeftParentNext() ) {
                            if ( childleftTuple.getLeftTupleSink() == sink ) {
                                return childleftTuple;
                            }
                        }
                    }

                }
                rightTuple = (RightTuple) localIt.next( rightTuple );
            }
        } else if ( source instanceof LeftInputAdapterNode ) {
            ObjectTypeNode otn = null;
            ObjectSource os = ((LeftInputAdapterNode) source).getParentObjectSource();
            while ( !(os instanceof ObjectTypeNode) ) {
                os = os.getParentObjectSource();
            }
            otn = (ObjectTypeNode) os;

            ObjectHashSet memory = ((ObjectTypeNodeMemory) wm.getNodeMemory( otn )).memory;
            otnIterator = memory.iterator();

            for ( factHandleEntry = (ObjectEntry) otnIterator.next(); factHandleEntry != null; factHandleEntry = (ObjectEntry) otnIterator.next() ) {
                InternalFactHandle handle = (InternalFactHandle) factHandleEntry.getValue();
                for ( LeftTuple leftTuple = handle.getFirstLeftTuple(); leftTuple != null; leftTuple = leftTuple.getLeftParentNext() ) {
                    if ( leftTuple.getLeftTupleSink() == sink ) {
                        return leftTuple;
                    }
                }
            }
        } else if ( source instanceof EvalConditionNode || source instanceof QueryElementNode ) {
            LeftTuple parentLeftTuple = null;
            
            if ( source instanceof EvalConditionNode ) {
                parentLeftTuple = getFirstLeftTuple( ((EvalConditionNode) source).getLeftTupleSource(),
                                                     (LeftTupleSink) source,
                                                     wm );
            } else {
                parentLeftTuple = getFirstLeftTuple( ((QueryElementNode) source).getLeftTupleSource(),
                                                     (LeftTupleSink) source,
                                                     wm );
            }
            while ( parentLeftTuple != null ) {
                for ( LeftTuple leftTuple = parentLeftTuple.getFirstChild(); leftTuple != null; leftTuple = leftTuple.getLeftParentNext() ) {
                    if ( leftTuple.getLeftTupleSink() == sink ) {
                        return leftTuple;
                    }
                }
                
                if ( source instanceof EvalConditionNode ) {
                    parentLeftTuple = getNextLeftTuple( ((EvalConditionNode) source).getLeftTupleSource(),
                                                         (LeftTupleSink) source,
                                                         parentLeftTuple,
                                                         wm );
                } else {
                    parentLeftTuple = getNextLeftTuple( ((QueryElementNode) source).getLeftTupleSource(),
                                                         (LeftTupleSink) source,
                                                         parentLeftTuple,
                                                         wm );
                }                
            }
        }
        return null;
    }

    public LeftTuple getNextLeftTuple(LeftTupleSource source,
                                      LeftTupleSink sink,
                                      LeftTuple leftTuple,
                                      InternalWorkingMemory wm) {

        if ( factHandleEntry != null ) {
            LeftTuple leftParent = leftTuple.getLeftParent();
            
            while ( leftTuple != null ) {
                leftTuple = leftTuple.getLeftParentNext();
                
                for ( ; leftTuple != null; leftTuple = leftTuple.getLeftParentNext() ) {
                    // Iterate to find the next left tuple for this sink, skip tuples for other sinks due to sharing split
                    if ( leftTuple.getLeftTupleSink() == sink ) {
                        return leftTuple;
                    }
                }
            }
                                    
            // We have a parent LeftTuple so try there next
            if ( leftParent != null ) {
                // we know it has to be evalNode query element node
                while ( leftParent != null ) {
                    if ( source instanceof EvalConditionNode ) {
                        leftParent = getNextLeftTuple( ((EvalConditionNode) source).getLeftTupleSource(),
                                                             (LeftTupleSink) source,
                                                             leftParent,
                                                             wm );
                    } else {
                        leftParent = getNextLeftTuple( ((QueryElementNode) source).getLeftTupleSource(),
                                                             (LeftTupleSink) source,
                                                             leftParent,
                                                             wm );
                    }                     

                    if ( leftParent != null ) {
                        for ( leftTuple = leftParent.getFirstChild(); leftTuple != null; leftTuple = leftTuple.getLeftParentNext() ) {
                            if ( leftTuple.getLeftTupleSink() == sink ) {
                                return leftTuple;
                            }
                        }
                    }
                }
                return null;
            }

            if ( factHandleEntry == null ) {
                // we've exhausted this OTN
                return null;
            }

            // We have exhausted the current FactHandle, now try the next                     
            for ( factHandleEntry = (ObjectEntry) otnIterator.next(); factHandleEntry != null; factHandleEntry = (ObjectEntry) otnIterator.next() ) {
                InternalFactHandle handle = (InternalFactHandle) factHandleEntry.getValue();
                for ( leftTuple = handle.getFirstLeftTuple(); leftTuple != null; leftTuple = leftTuple.getLeftParentNext() ) {
                    if ( leftTuple.getLeftTupleSink() == sink ) {
                        return leftTuple;
                    }
                }
            }
            // We've exhausted this OTN so set the iterator to null
            factHandleEntry = null;
            otnIterator = null;

        } else if ( source instanceof JoinNode || source instanceof NotNode|| source instanceof FromNode || source instanceof AccumulateNode ) {
            BetaMemory memory;
            FastIterator localIt;
            if ( source instanceof FromNode ) {
                memory = ((FromMemory) wm.getNodeMemory( (MemoryFactory) source )).betaMemory;
            } else if ( source instanceof AccumulateNode ) {
                memory = ((AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source )).betaMemory;
            } else {
                memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
            }

            localIt = memory.getLeftTupleMemory().fullFastIterator( leftTuple.getLeftParent() );

            LeftTuple childLeftTuple = leftTuple;
            if ( childLeftTuple != null ) {
                leftTuple = childLeftTuple.getLeftParent();

                while ( leftTuple != null ) {
                    if ( childLeftTuple == null ) {
                        childLeftTuple = leftTuple.getFirstChild();
                    } else {
                        childLeftTuple = childLeftTuple.getLeftParentNext();
                    }
                    for ( ; childLeftTuple != null; childLeftTuple = childLeftTuple.getLeftParentNext() ) {
                        if ( childLeftTuple.getLeftTupleSink() == sink ) {
                            return childLeftTuple;
                        }
                    }
                    leftTuple = (LeftTuple) localIt.next( leftTuple );
                }
            }
        }
        if ( source instanceof ExistsNode ) {
            BetaMemory memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );

            RightTuple rightTuple = leftTuple.getLeftParent().getBlocker();
            FastIterator localIt = memory.getRightTupleMemory().fullFastIterator( rightTuple );

            for ( LeftTuple childleftTuple = leftTuple.getLeftParentNext(); childleftTuple != null; childleftTuple = childleftTuple.getLeftParentNext() ) {
                if ( childleftTuple.getLeftTupleSink() == sink ) {
                    return childleftTuple;
                }
            }

            leftTuple = leftTuple.getLeftParent();

            // now move onto next RightTuple                                                
            while ( rightTuple != null ) {
                if ( rightTuple.getBlocked() != null ) {
                    if ( leftTuple != null ) {
                        leftTuple = leftTuple.getBlockedNext();
                    } else {
                        leftTuple = rightTuple.getBlocked();
                    }
                    for ( ; leftTuple != null; leftTuple = leftTuple.getBlockedNext() ) {
                        for ( LeftTuple childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getLeftParentNext() ) {
                            if ( childleftTuple.getLeftTupleSink() == sink ) {
                                return childleftTuple;
                            }
                        }
                    }

                }
                rightTuple = (RightTuple) localIt.next( rightTuple );
            }

        } else if ( source instanceof EvalConditionNode || source instanceof QueryElementNode ) {
            LeftTuple childLeftTuple = leftTuple;
            if ( leftTuple != null ) {
                leftTuple = leftTuple.getLeftParent();

                while ( leftTuple != null ) {
                    if ( childLeftTuple != null ) {
                        childLeftTuple = childLeftTuple.getLeftParentNext();
                    } else {
                        childLeftTuple = leftTuple.getFirstChild();
                    }
                    for ( ; childLeftTuple != null; childLeftTuple = childLeftTuple.getLeftParentNext() ) {
                        if ( childLeftTuple.getLeftTupleSink() == sink ) {
                            return childLeftTuple;
                        }
                    }

                    if ( source instanceof EvalConditionNode ) {
                        leftTuple = getNextLeftTuple( ((EvalConditionNode) source).getLeftTupleSource(),
                                                             (LeftTupleSink) source,
                                                             leftTuple,
                                                             wm );
                    } else {
                        leftTuple = getNextLeftTuple( ((QueryElementNode) source).getLeftTupleSource(),
                                                             (LeftTupleSink) source,
                                                             leftTuple,
                                                             wm );
                    } 
                }
            }
        }
        return null;
    }

    public void setNextLeftTuple() {
        LeftTupleSource source = node.getLeftTupleSource();
        currentLeftTuple = getNextLeftTuple( source,
                                             (LeftTupleSink) node,
                                             currentLeftTuple,
                                             wm );
    }

    public Object next() {
        LeftTuple leftTuple = null;
        if ( this.currentLeftTuple != null ) {
            leftTuple = currentLeftTuple;
            setNextLeftTuple();
        }

        return leftTuple;
    }

}
