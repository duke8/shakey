package shakey.internal

import org.apache.tapestry5.json.{JSONArray, JSONObject}
import shakey.services.{Stock, LoggerSupport}
import scala.collection.mutable.ArrayBuffer
import util.control.Breaks._
import shakey.ShakeyConstants


/**
 * Created by jcai on 14-9-26.
 */
object StockSymbolFetcher extends LoggerSupport {
  private val url_formatter = "http://stock.finance.sina.com.cn/usstock/api/jsonp.php/x/US_CategoryService.getList?page=%s&num=60&sort=price&asc=0&market=&id="

  def main(args: Array[String]) {
    val stocks = fetchChinaStock.mkString(",")
    println(stocks)
  }

  def fetchAllStock {
    1 to 200 foreach {
      case page =>
        val content = RestClient.get(url_formatter.format(page), encoding = "GBK")
        val jsonObject = new JSONObject(content.substring(3, content.length - 3))
        val count = jsonObject.getInt("count")
        val data = jsonObject.getJSONArray("data")

        0 until data.length() foreach {
          case j =>
            //{count:"8318",data:[{name:"Goldman Sachs Group Inc.",cname:"高盛集团",category:"",symbol:"GS",price:"184.09",diff:"-3.72",chg:"-1.98",preclose:"187.81",open:"187.46",high:"187.80",low:"183.46",amplitude:"2.31%",volume:"2999670",mktcap:"84400008279",pe:"12.12714049",market:"NYSE",category_id:"695"},
            println("\"" + data.getJSONObject(j).getString("symbol") + "\",")
        }
    }
  }

  private val cn_stock_formatter = "http://money.finance.sina.com.cn/q/api/jsonp_v2.php/x/US_ChinaStockService.getData?page=%s&num=60&sort=volume&asc=0&market=&concept=0";

  def fetchChinaStock = {
    val buffer = new ArrayBuffer[String]()
    var count = 0
    breakable {
      1 to 4 foreach {
        case page =>
          val content = RestClient.get(cn_stock_formatter.format(page), encoding = "GBK")
          val jsonStr = content.substring(58, content.length - 1)
          val data = new JSONArray(jsonStr)
          0 until data.length() foreach {
            case j =>
              //{count:"8318",data:[{name:"Goldman Sachs Group Inc.",cname:"高盛集团",category:"",symbol:"GS",price:"184.09",diff:"-3.72",chg:"-1.98",preclose:"187.81",open:"187.46",high:"187.80",low:"183.46",amplitude:"2.31%",volume:"2999670",mktcap:"84400008279",pe:"12.12714049",market:"NYSE",category_id:"695"},
              val obj = data.getJSONObject(j)
              if (obj.getInt("volume") > 200000 && obj.getDouble("open") > 5.0) {
                buffer += obj.getString("symbol")
                count += 1
              }
              if (count >= 100) {
                //IB的API处理只能处理100个stock
                break;
              }
          }
      }
    }

    buffer.toArray
  }

  def fetchStockRateByDayVolume(stock: Stock, rateOverflow: Double) {
    val content = RestClient.get(ShakeyConstants.HISTORY_API_URL_FORMATTER.format(stock.symbol))
    val jsonArray = new JSONArray(content)
    val len = jsonArray.length()
    var size = ShakeyConstants.HISTORY_SIZE
    var begin = len - size
    if (begin < 0)
      begin = 0
    size = len - begin
    var volCount = 0
    begin until jsonArray.length() foreach {
      case i =>
        val obj = jsonArray.getJSONObject(i)
        volCount += obj.getInt("v")
    }
    val rate: Double = (volCount / 1.0 / size / ShakeyConstants.TRADE_SECONDS_IN_ONE_DAY / 100) * rateOverflow
    logger.debug("symbol:{} rate:{}", stock.symbol, rate)
    stock.rateOneSec = rate;
  }
}
