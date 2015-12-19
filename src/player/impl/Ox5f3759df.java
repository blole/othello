package player.impl;

import java.util.LinkedList;
import java.util.Random;

import game.Position;
import player.Board;
import player.Player;
import game.COLOR;

public class Ox5f3759df extends Player
{
    private COLOR oppCOLOR;
    private Random rand = new Random();

	class Node
	{
		Node parent = null;
		Position pos = null;
		long visits = 0;
		double reward = 0;
		Node[] children = null;
		
		Node()
		{}
		
		Node(Node parent, Position pos)
		{
			this.parent = parent;
			this.pos = pos;
		}
		
		Node(Node parent, Position pos, long visits, double reward)
		{
			this.parent = parent;
			this.pos = pos;
			this.visits = visits;
			this.reward = reward;
		}
		
		double avgReward()
		{
			return reward/visits;
		}
		
		//upper confidence bound
		double ucb()
		{
			return avgReward() + Math.sqrt(Math.log(parent.avgReward()));
		}
		
		public boolean expanded()
		{
			return children != null;
		}

		public int leafs()
		{
			if (!expanded())
				return 1;
			else
			{
				int totalLeafs = 0;
				for (Node child : children)
					totalLeafs += child.leafs();
				return totalLeafs;
			}
		}
	}
	
	public Ox5f3759df(game.COLOR color)
	{
		super(color);
	}

	@Override
	public void newGame()
	{
        if (this.COLOR == game.COLOR.BLACK) {
            this.oppCOLOR = game.COLOR.WHITE;
        } else {
            this.oppCOLOR = game.COLOR.BLACK;
        }
	}

	@Override
	public Position nextMove()
	{
		Node root = new Node();
		
		return nextMove(currentBoard, root, System.currentTimeMillis()+1500);
	}
	
	private static game.COLOR reverseColor(game.COLOR color) {
		if (color == game.COLOR.BLACK)
			return game.COLOR.WHITE;
		else
			return game.COLOR.BLACK;
	}

	private Position nextMove(Board rootBoard, Node root, long timeout)
	{
		while (timeout > System.currentTimeMillis())
		{
			Board board = rootBoard.copy();
			Node node = root;
			game.COLOR currentColor = COLOR;
			
			while (!board.gameIsFinished())
			{
				boolean wasExpanded = node.expanded();
				if (!node.expanded()) //expand?
				{
					LinkedList<Position> moves = board.getAllLegalMoves(currentColor);
					if (moves == null || moves.isEmpty())
						node.children = new Node[]{new Node(node, null)}; //null move, can't move
					else
					{
						node.children = new Node[moves.size()];
						int i=0;
						for (Position move : moves)
							node.children[i++] = new Node(node, move);
					}
				}
				
				node = select(board, node, currentColor);
				
				if (!wasExpanded)
					break;
				
				//selection
				if (node.pos != null)
				{
					if (!board.placeDisk(currentColor, node.pos))
						throw new RuntimeException("invalid move?");
				}
				currentColor = reverseColor(currentColor);
			}
			
			//simulate
			//double score = evaluate(board);
			double score = playout(board, currentColor);
			
			backpropagate(board, node, score);
		}
		
		System.out.printf("root.leafs(): %d\n", root.leafs());
		System.out.printf("root.visits: %d\n", root.visits);
		System.out.printf("root.avgReward(): %.2f\n", root.avgReward());
		
		return select(rootBoard, root, COLOR).pos;
	}

	private double playout(Board currentBoard, game.COLOR currentColor)
	{
		int playouts = 8;
		double totalScore = 0;
		for (int i=0; i<playouts; i++)
		{
			Board board = currentBoard.copy();
			game.COLOR color = currentColor;
			int workaround = 0;
			while (!board.gameIsFinished() && workaround < 120)
			{
				LinkedList<Position> moves = board.getAllLegalMoves(color);
				if (moves != null && !moves.isEmpty())
				{
					if (!board.placeDisk(color, moves.get(rand.nextInt(moves.size()))))
						throw new RuntimeException("invalid move?");
				}
				color = reverseColor(color);
				workaround++;
			}
			totalScore += evaluate(board);
		}
		return totalScore/playouts;
	}
	
	private char colorChar(game.COLOR c)
	{
		return c==COLOR.BLACK?'x':c==COLOR.WHITE?'o':'.';
	}
	
	private void backpropagate(Board board, Node node, double score)
	{
		do
		{
			node.visits++;
			node.reward += score;
			node = node.parent;
		}
		while (node != null);
	}
	
    /** returns end game evaluation (our score) normalized to [-1, 1]**/
    private double evaluate(Board board) {

        double myScore, oppScore, numEmpty;
        myScore = oppScore = numEmpty = 0.0d;

        COLOR[][] cm = board.getColorMatrix();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (cm[i][j] == game.COLOR.EMPTY) {
                    numEmpty++;
                } else if (cm[i][j] == this.COLOR) {
                    myScore++;
                } else {
                    oppScore++;
                }
            }
        }

        if (myScore <= oppScore)
            return myScore/32-1;
        else // myScore > oppScore
            return (myScore+numEmpty)/32-1;
    }

    /** returns reward for current board state (not normalized) **/
    private double evaluateMove(Board board) {
        double myScore, oppScore;
        myScore = oppScore = 0.0d;

        int[][] weights = {{20, -3, 11, 8, 8, 11, -3, 20},
                {-3, -7, -4, 1, 1, -4, -7, -3},
                {11, -4,  2, 2, 2,  2, -4, 11},
                { 8,  1,  2,-3,-3,  2,  1,  8},
                { 8,  1,  2,-3,-3,  2,  1,  8},
                {11, -4,  2, 2, 2,  2, -4, 11},
                {-3, -7, -4, 1, 1, -4, -7, -3},
                {20, -3, 11, 8, 8, 11, -3, 20}};

        COLOR[][] cm = board.getColorMatrix();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (cm[i][j] == this.COLOR) {
                    myScore += weights[i][j];
                } else if (cm[i][j] == this.oppCOLOR) {
                    oppScore += weights[i][j];
                }
            }
        }
        return myScore-oppScore;
    }

	private Node select(Board board, Node node, game.COLOR color)
	{
		int factor = color == COLOR?1:-1;
		
		Node best = node.children[0];
		double bestUCB = factor * best.ucb();
		for (int i=1; i<node.children.length; i++)
		{
			double ucb = factor * node.children[i].ucb();
			if (bestUCB < ucb)
			{
				best = node.children[i];
				bestUCB = ucb;
			}
		}
		return best;
	}
}
