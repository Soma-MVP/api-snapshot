package pl.itcraft.soma.core.utils;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.dto.BlockchainTransactionStatusDto;
import pl.itcraft.soma.core.objectify.OfyHelper;

public class BlockchainUtils {
	
	private static final Logger logger = Logger.getLogger(BlockchainUtils.class.getName());
	
	public static String sendTransaction(String signedTransaction) {
		if (StringUtils.isBlank(signedTransaction)) {
			logger.warning("Transaction is empty");
			return null;
		}
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		Request<?, EthSendTransaction> req = web3.ethSendRawTransaction(signedTransaction);
		try {
			EthSendTransaction transaction = req.send();
			String transactionHash = transaction.getTransactionHash();
			logger.info("Transaction hash: " + transactionHash);
			if (transaction.getError() != null) {
				logger.warning(transaction.getError().getMessage());
				logger.warning(transaction.getError().getData());
			}
			return transactionHash;
		} catch (IOException e) {
			logger.warning("IOException in sending transaction to blockchain");
			throw new RuntimeException(e);
		}
	}
	
	public static String getItemBlockchainIdFromTransactionHash(Web3j web3, String transactionHash) {
		Request<?, EthGetTransactionReceipt> receipt = web3.ethGetTransactionReceipt(transactionHash);
		Optional<String> blockchainId;
		try {
			blockchainId = receipt.send().getTransactionReceipt().map(tr -> Numeric.toBigInt(tr.getLogs().get(0).getData()).toString());
			return blockchainId.orElse(null);
		} catch (IOException e) {
			logger.warning("IOException in getting item blockchain id from transaciton hash");
			throw new RuntimeException(e);
		}
	}
	
	public static BigInteger getNextAvailableNonce(Web3j web3, String address) {
		try {
			EthGetTransactionCount ethGetTransactionCount = web3.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send();
			BigInteger nonce = ethGetTransactionCount.getTransactionCount();
			return nonce;
		} catch (IOException e) {
			logger.warning("IOException in getting next available nonce for address: " + address);
			throw new RuntimeException(e);
		}
	}
	
	public static String getOwnerOfIIC(String address, String blockchainId) {
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		Function function = new Function(
			"ownerOf",
			Arrays.asList(new Uint256(new BigInteger(blockchainId))),
			Arrays.asList(new TypeReference<Address>() {})
		);
		String encodedFunction = FunctionEncoder.encode(function);
		try {
			EthCall response = web3.ethCall(Transaction.createEthCallTransaction(address, Constants.IIC_CONTRACT_ADDRESS, encodedFunction), DefaultBlockParameterName.LATEST).send();
			List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			String owner = ((Address)results.get(0)).getValue();
			return owner;
		} catch (IOException e) {
			logger.warning("IOException in sending transaction to blockchain");
			throw new RuntimeException(e);
		}
	}
	
	public static String mintTestSCT(String address, BigInteger amount) {
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		Function function = new Function(
			"mint",
			Arrays.asList(new Address(address), new Uint256(amount)),
			Collections.<TypeReference<?>>emptyList()
			);
		try {
			Credentials credentials = WalletUtils.loadCredentials(Constants.ETHEREUM_WALLET_PASSWORD, OfyHelper.getServletContext().getRealPath("WEB-INF/ethereum_wallet.json"));
			BigInteger nonce = BlockchainUtils.getNextAvailableNonce(web3, credentials.getAddress());
			RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, Constants.GAS_PRICE, Constants.GAS_LIMIT, Constants.SCT_CONTRACT_ADDRESS, FunctionEncoder.encode(function));
			byte[] signedTransaction = TransactionEncoder.signMessage(rawTransaction, credentials);
			return BlockchainUtils.sendTransaction(Numeric.toHexString(signedTransaction));
		} catch (IOException e) {
			logger.warning("IOException in minting Test SCT");
			throw new RuntimeException(e);
		} catch (CipherException e) {
			logger.warning("CipherException in minting Test SCT");
			throw new RuntimeException(e);
		}
	}
	
	public static BlockchainTransactionStatusDto checkTransactionStatus(Web3j web3, String transactionHash) {
		Request<?, EthGetTransactionReceipt> receipt = web3.ethGetTransactionReceipt(transactionHash);
		try {
			Optional<TransactionReceipt> optionalReceipt = receipt.send().getTransactionReceipt();
			if (!optionalReceipt.isPresent()) {
				return BlockchainTransactionStatusDto.pending();
			}
			TransactionReceipt transactionReceipt = optionalReceipt.get();
			if (Numeric.toBigInt(transactionReceipt.getStatus()).intValue() == 1) {
				return BlockchainTransactionStatusDto.success();
			}
			//TODO set some meaningful error message
			return BlockchainTransactionStatusDto.fail(null);
		} catch (IOException e) {
			logger.warning("IOException in checking transaction status, transactionHash: " + transactionHash);
			throw new RuntimeException(e);
		}
	}
	
	public static BigInteger getSCTBalance(String address) {
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		Function function = new Function(
			"balanceOf",
			Arrays.asList(new Address(address)),
			Arrays.asList(new TypeReference<Uint256>() {})
		);
		String encodedFunction = FunctionEncoder.encode(function);
		try {
			EthCall response = web3.ethCall(Transaction.createEthCallTransaction(address, Constants.SCT_CONTRACT_ADDRESS, encodedFunction), DefaultBlockParameterName.LATEST).send();
			List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			BigInteger balance = ((Uint256)results.get(0)).getValue();
			return balance;
		} catch (IOException e) {
			logger.warning("IOException in sending transaction to blockchain");
			throw new RuntimeException(e);
		}
	}
}
