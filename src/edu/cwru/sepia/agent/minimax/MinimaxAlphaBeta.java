package edu.cwru.sepia.agent.minimax;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;

public class MinimaxAlphaBeta extends Agent {
	private static final long serialVersionUID = 1L;
	private final int numPlys;

    public MinimaxAlphaBeta(int playernum, String[] args)
    {
        super(playernum);

        if(args.length < 1)
        {
            System.err.println("You must specify the number of plys");
            System.exit(1);
        }

        numPlys = Integer.parseInt(args[0]);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        GameStateChild bestChild = alphaBetaSearch(new GameStateChild(newstate),
                numPlys,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);

        return bestChild.action;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {

    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this.
     *
     * This is the main entry point to the alpha beta search. Refer to the slides, assignment description
     * and book for more information.
     *
     * Try to keep the logic in this function as abstract as possible (i.e. move as much SEPIA specific
     * code into other functions and methods)
     *
     * @param node The action and state to search from
     * @param depth The remaining number of plys under this node
     * @param alpha The current best value for the maximizing node from this node to the root
     * @param beta The current best value for the minimizing node from this node to the root
     * @return The best child of this node with updated values
     */
    public GameStateChild alphaBetaSearch(GameStateChild node, int depth, double alpha, double beta){
        double value = maxValue(node, depth, alpha, beta);
        return getStateWithValue(node, value);
    }
    
    private GameStateChild getStateWithValue(GameStateChild node, double value) {    
    	for(GameStateChild child : node.state.getChildren()){
    		if(child.state.getUtility() == value){
    			return child;
    		}
    	}
    	return null;
	}

	private double maxValue(GameStateChild node, int depth, double alpha, double beta){
    	if(cutOffTest(node, depth)){
    		return node.state.getUtility();
    	}
    	double value = Double.NEGATIVE_INFINITY;
		for(GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren())){
    		value = Math.max(value, minValue(child, depth - 1, alpha, beta));
    		if(value >= beta){
    			return value;
    		}
    		alpha = Math.max(alpha, value);
    	}
    	return value;
    }
    
	private double minValue(GameStateChild node, int depth, double alpha, double beta) {
		if(cutOffTest(node, depth)){
			return node.state.getUtility();
		}
		double value = Double.POSITIVE_INFINITY; 
		for(GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren())){
			value = Math.min(value, maxValue(child, depth - 1, alpha, beta));
			if(value <= alpha){
				return value;
			}
			beta = Math.min(beta, value);
		}
		return value;
	}

	private boolean cutOffTest(GameStateChild node, int depth) {
		return depth == 0 || node.state.getUtility() == Double.POSITIVE_INFINITY;
	}


	/**
     * You will implement this.
     *
     * Given a list of children you will order them according to heuristics you make up.
     * See the assignment description for suggestions on heuristics to use when sorting.
     *
     * Use this function inside of your alphaBetaSearch method.
     *
     * Include a good comment about what your heuristics are and why you chose them.
     *
     * @param children
     * @return The list of children sorted by your heuristic.
     */
    public List<GameStateChild> orderChildrenWithHeuristics(List<GameStateChild> children){
        children.sort((o1, o2) -> {
        	if(o1.state.getUtility() > o2.state.getUtility()){
        		return -1;
        	} else if (o1.state.getUtility() < o2.state.getUtility()){
        		return 1;
        	} else {
        		return 0;
        	}
        });
        
        List<GameStateChild> ordered = new LinkedList<GameStateChild>();
        for(GameStateChild child : children){
        	int count = 0;
        	for(Action action : child.action.values()){
        		if(action.getType().name().equals(Action.createPrimitiveAttack(0, 0).getType().name())){
        			count++;
        		}
        	}
        	if(count == child.action.size()){
        		ordered.add(0, child);
        	} else if (count > 0){
        		if(ordered.isEmpty()){
        			ordered.add(0, child);
        		} else {
        			ordered.add(1, child);
        		}
        	} else {
        		ordered.add(child);
        	}
        }
        //ordered.addAll(children);
        return ordered;
    }
}
