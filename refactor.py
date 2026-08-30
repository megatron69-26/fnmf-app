import re

with open('app/src/main/java/com/example/nhumonglenh/TradingFragment.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Replace class definition
code = code.replace('class Activity2 : AppCompatActivity()', 'import androidx.fragment.app.Fragment\nimport android.view.ViewGroup\nimport androidx.recyclerview.widget.RecyclerView\nimport androidx.recyclerview.widget.LinearLayoutManager\nimport android.content.Context\n\nclass TradingFragment : Fragment()')
code = code.replace('class Activity2 : AppCompatActivity() {', 'class TradingFragment : Fragment() {')

# Find/Replace onCreate to onCreateView and onViewCreated
code = code.replace('override fun onCreate(savedInstanceState: Bundle?) {', '''override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)''')
code = code.replace('super.onCreate(savedInstanceState)', '// super.onCreate(savedInstanceState)')
code = code.replace('supportActionBar?.hide()', '// supportActionBar?.hide()')
code = code.replace('setContentView(R.layout.layout_activity2)', '')

# Replace findViewById
code = re.sub(r'findViewById<([^>]+)>\(([^)]+)\)', r'view.findViewById<\1>(\2)', code)
code = re.sub(r'(?<!view\.)findViewById\(([^)]+)\)', r'view.findViewById(\1)', code)

# Replace 'this' where appropriate
code = code.replace('this@Activity2', 'requireActivity()')
code = code.replace('AppDatabase.getInstance(this)', 'AppDatabase.getInstance(requireContext())')
code = code.replace('getSharedPreferences("fnmf_prefs", MODE_PRIVATE)', 'requireActivity().getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)')
code = code.replace('LayoutInflater.from(this)', 'LayoutInflater.from(requireContext())')
code = code.replace('LayoutInflater.from(this@Activity2)', 'LayoutInflater.from(requireContext())')
code = code.replace('Toast.makeText(this', 'Toast.makeText(requireContext()')
code = code.replace('runOnUiThread', 'activity?.runOnUiThread')

# Change TAG
code = code.replace('"FNMF_Activity2"', '"FNMF_TradingFragment"')

# Add Order History logic
code = code.replace('private lateinit var lvWatchlist: ListView', '''private lateinit var lvWatchlist: ListView
    private lateinit var rvOrderHistory: RecyclerView
    private lateinit var orderAdapter: OrderHistoryAdapter''')
code = code.replace('lvWatchlist = view.findViewById(R.id.lvWatchlist)', '''lvWatchlist = view.findViewById(R.id.lvWatchlist)
        rvOrderHistory = view.findViewById(R.id.rvOrderHistory)
        orderAdapter = OrderHistoryAdapter(emptyList())
        rvOrderHistory.layoutManager = LinearLayoutManager(requireContext())
        rvOrderHistory.adapter = orderAdapter''')

# Inside loadPortfolio
code = code.replace('updatePortfolioDisplay()\n                }', '''updatePortfolioDisplay()
                    loadOrderHistory()
                }''')

# Add loadOrderHistory function
load_order_history_func = '''
    private fun loadOrderHistory() {
        if (jwtToken.isEmpty()) return
        val authHeader = "Bearer $jwtToken"
        RetrofitClient.apiService.getOrderHistory(authHeader).enqueue(object : Callback<List<OrderResponse>> {
            override fun onResponse(call: Call<List<OrderResponse>>, response: Response<List<OrderResponse>>) {
                if (response.isSuccessful) {
                    val orders = response.body() ?: emptyList()
                    orderAdapter.updateOrders(orders)
                }
            }
            override fun onFailure(call: Call<List<OrderResponse>>, t: Throwable) {
                Log.e(TAG, "Lỗi tải lịch sử giao dịch: ${t.message}")
            }
        })
    }
'''
code = code.replace('private fun updateHoldingsForCurrentSymbol()', load_order_history_func + '\n    private fun updateHoldingsForCurrentSymbol()')

with open('app/src/main/java/com/example/nhumonglenh/TradingFragment.kt', 'w', encoding='utf-8') as f:
    f.write(code)
