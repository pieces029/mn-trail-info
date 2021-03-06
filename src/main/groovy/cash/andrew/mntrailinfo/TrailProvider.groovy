package cash.andrew.mntrailinfo

import cash.andrew.mntrailinfo.model.TrailInfo
import cash.andrew.mntrailinfo.model.TrailRegion
import com.google.inject.Inject
import com.google.inject.Singleton
import groovy.util.logging.Slf4j
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import ratpack.exec.Promise
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse

import static com.google.common.base.Preconditions.checkNotNull

@Slf4j
@Singleton
class TrailProvider {

  // todo put in real url when it becomes available
  private static final URI V3_API_ENDPOINT = URI.create('https://pa2jiiv7i1.execute-api.us-east-1.amazonaws.com/dev/trails')

  private final TrailWebsiteProvider websiteProvider
  private final TrailWebsiteDateParser parser
  private final HttpClient httpClient

  @Inject TrailProvider(TrailWebsiteProvider websiteProvider, TrailWebsiteDateParser parser, HttpClient httpClient) {
    this.websiteProvider = checkNotNull(websiteProvider, 'websiteProvider == null')
    this.parser = checkNotNull(parser, 'parser == null')
    this.httpClient = checkNotNull(httpClient, 'httpClient == null')
  }

  Promise<List<TrailRegion>> provideTrails() {
    websiteProvider.website.map { String websiteHtml ->
      def trails = []
      def doc = Jsoup.parse(websiteHtml)
      def dataTable = doc.getElementsByClass('forumbits')

      doc.getElementsByClass('conditionhead').eachWithIndex { Element tableRow, int i ->
        def trailInfoList = []
        dataTable[i].getElementsByTag('tr')[1..-1].each { Element tr ->
          def td = tr.children()
          def name = td[0].text()
          def status = td[1].text()
          def description = td[3].text()
          def lastUpdated = td[5].text()

          def trailInfo = new TrailInfo(
              name: name,
              status: status,
              description: description,
              lastUpdated: parser.parseText(lastUpdated)
              )
          trailInfoList << trailInfo
        }

        def title = tableRow.child(0).child(1).text()
        def trailRegion = new TrailRegion(name: title, trails: trailInfoList)

        trails << trailRegion
      }

      return trails
    }
  }

  Promise<List<TrailInfo>> provideTrailsV2() {
    return websiteProvider.website.observe().map { String trailConditionsHtml ->
      return Jsoup.parse(trailConditionsHtml)
    }.map { Document doc ->
      def dataTable = doc.getElementsByClass('forumbits')

      def returnValue = []
      doc.getElementsByClass('conditionhead').eachWithIndex { Element tableRow, int i ->
        dataTable[i].getElementsByTag('tr')[1..-1].each { Element tr ->
          returnValue << tr.children()
        }
      }
      return returnValue
    }.flatMapIterable { data -> data }
    .flatMap { Elements td ->
      def trailConditionThread = td[0].child(0).attr('href').split('=')[1]
      return websiteProvider.trailDetails(trailConditionThread)
          .observe()
          .map { String detailsHtml ->
            return Jsoup.parse(detailsHtml)
          }.map { Document detailsDoc ->
            detailsDoc.getElementsByClass('content')[0].text().split('Details:')[1].trim()
          }.map { String fullDescription ->
            def name = td[0].text().trim()
            def status = td[1].text().trim()
            def description = td[3].text().trim()
            def lastUpdated = td[5].text().trim()

            def trailInfo = new TrailInfo(
                name: name,
                status: status,
                description: description,
                fullDescription: fullDescription,
                lastUpdated: parser.parseText(lastUpdated)
                )

            return trailInfo
          }
    }.promise()
  }

  /**
   * Proxy to the new trail service.
   */
  Promise<ReceivedResponse> provideTrailsV3() {
    return httpClient.get(V3_API_ENDPOINT)
  }
}
